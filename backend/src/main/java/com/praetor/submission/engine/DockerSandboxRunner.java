package com.praetor.submission.engine;

import com.praetor.submission.config.JudgeProperties;
import com.praetor.submission.entity.JudgeTestCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Real sandbox: compiles and runs submitted code in a locked-down sibling docker container
 * ({@code --network none --memory --cpus --pids-limit}), sharing the per-run work dir with the
 * backend through the named volume {@code praetor_work} mounted at {@code workDir} in both.
 *
 * <p>Per-run isolation: everything lives under {@code <workDir>/<runId>/}; {@link #cleanup} removes
 * only that subtree, so concurrent workers never collide. Timing + peak memory are measured inside
 * the container by {@code /usr/bin/time -v} (written to {@code time.txt} on the shared volume), so
 * MLE is decided by measured RSS rather than the ambiguous exit-137 signal.
 *
 * <p><b>One container per submission, not per test case.</b> {@link #compile} stands up a container
 * running {@code sleep infinity} and every phase after it is a {@code docker exec} into that
 * container; {@link #cleanup} tears it down. Launching a container measured ~890 ms on the dev
 * machine against ~60 ms for an exec, and a submission pays that once per case — a 10-case problem
 * spent roughly 8 of its 9 seconds starting containers. The caps are unchanged: they are simply set
 * once at creation instead of once per case.
 *
 * <p><b>Why an exit code is read from a file.</b> A {@code docker run} exits with the program's
 * status, so the old path could trust it. {@code docker exec} cannot be trusted the same way: if
 * the container is gone the CLI itself exits non-zero, which is indistinguishable from a submission
 * that returned non-zero — it would be reported as RE. A container CAN die mid-submission, because
 * a program that exhausts {@code --memory} may take the container's init down with it, which is
 * exactly what an MLE submission does. So each phase writes {@code $?} to {@code exit.txt} on the
 * shared volume: present means the command really ran and the code is authoritative, absent means
 * it did not run at all. On absent, the case is redone through the original one-shot
 * {@code docker run --rm} and the submission stays on that path — degrading to the old speed,
 * never to a wrong verdict.
 */
@Component
@Primary
public class DockerSandboxRunner implements SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxRunner.class);
    private static final int STDOUT_CAP = 256 * 1024; // output-flood cap → truncated → RE
    private static final int COMPILE_CAP = 64 * 1024;
    private static final long COMPILE_TIMEOUT_MS = 30_000;
    private static final Pattern ELAPSED =
            Pattern.compile("Elapsed \\(wall clock\\) time.*?:\\s*([0-9:.]+)");
    private static final Pattern MAXRSS =
            Pattern.compile("Maximum resident set size \\(kbytes\\):\\s*(\\d+)");

    private final JudgeProperties props;
    private final DockerExecUtil docker;

    /** Run ids whose shared container is alive and usable. Absent = use the one-shot path. */
    private final Set<String> liveContainers = ConcurrentHashMap.newKeySet();

    public DockerSandboxRunner(JudgeProperties props, DockerExecUtil docker) {
        this.props = props;
        this.docker = docker;
    }

    @Override
    public CompileResult compile(String runId, Language language, String sourceCode, RunLimits limits) {
        Path dir = runDir(runId);
        try {
            Files.createDirectories(dir);
            // world-writable: the backend writes as root, the uid-1000 judge user writes prog/time.txt
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
            Files.writeString(dir.resolve(language.sourceFile()), sourceCode == null ? "" : sourceCode);
        } catch (IOException e) {
            throw new SandboxException("prepare work dir failed for " + runId, e);
        }

        if (props.reuseContainer()) {
            startContainer(runId, limits);
        }

        // For interpreted languages compileCmd is a syntax check (py_compile) — a syntax error still
        // exits non-zero with stderr and is reported as CE, same path as a C++ compile error.
        Phase phase = execute(runId, limits, language.compileCmd(), COMPILE_CAP, COMPILE_TIMEOUT_MS);
        if (phase.hostTimedOut()) {
            return new CompileResult(false, "Compilation timed out.");
        }
        if (phase.exitCode() != null && phase.exitCode() == 0) {
            return new CompileResult(true, "");
        }
        return new CompileResult(false, phase.stderr());
    }

    @Override
    public RunResult run(String runId, Language language, JudgeTestCase testCase, RunLimits limits) {
        Path dir = runDir(runId);
        try {
            Files.writeString(dir.resolve("input.txt"),
                    testCase.getInput() == null ? "" : testCase.getInput());
        } catch (IOException e) {
            throw new SandboxException("write input failed for " + runId, e);
        }
        int hardWallSec = Math.max(1, (int) Math.ceil(limits.hardWallMs() / 1000.0));
        // /usr/bin/time -v -o time.txt   timeout -s KILL <hard>s   <run cmd> < input.txt
        List<String> command = List.of("/usr/bin/time", "-v", "-o", "time.txt",
                "timeout", "-s", "KILL", hardWallSec + "s");
        List<String> full = new ArrayList<>(command);
        full.addAll(language.runCmd());

        long hostTimeout = limits.hardWallMs() + 15_000L; // slack above the container timer for startup
        Phase phase = execute(runId, limits, full, STDOUT_CAP, hostTimeout, "input.txt");

        Metrics m = readMetrics(dir);
        int wallMs = (m.wallMs() >= 0) ? m.wallMs() : phase.hostWallMs();
        Integer memKb = (m.memKb() >= 0) ? m.memKb() : null;
        Integer exit = phase.hostTimedOut() ? null : phase.exitCode();
        // timeout -s KILL exits 137, plain timeout exits 124; both may coincide with a real OOM 137,
        // so confirm a time-based kill via the measured wall clock.
        boolean timedOut = phase.hostTimedOut()
                || (exit != null && (exit == 124
                    || (exit == 137 && wallMs >= 0.9 * limits.hardWallMs())));
        return new RunResult(exit, phase.stdout(), wallMs, memKb, timedOut, phase.truncated());
    }

    @Override
    public void cleanup(String runId) {
        removeContainer(runId);
        Path dir = runDir(runId);
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort
                    }
                });
            }
        } catch (IOException e) {
            log.warn("cleanup failed for {}: {}", runId, e.getMessage());
        }
    }

    // --- execution -----------------------------------------------------------------------------

    /** One command's outcome, however it was executed. */
    private record Phase(Integer exitCode, String stdout, String stderr, boolean truncated,
                         boolean hostTimedOut, int hostWallMs) {
    }

    private Phase execute(String runId, RunLimits limits, List<String> command,
                          int stdoutCap, long hostTimeoutMs) {
        return execute(runId, limits, command, stdoutCap, hostTimeoutMs, null);
    }

    /**
     * Runs {@code command} in the run dir, preferring the submission's shared container. Falls back
     * to a one-shot {@code docker run --rm} when there is no live container, or when the exec turns
     * out not to have run the command at all.
     */
    private Phase execute(String runId, RunLimits limits, List<String> command,
                          int stdoutCap, long hostTimeoutMs, String stdinFile) {
        if (liveContainers.contains(runId)) {
            Phase phase = execInContainer(runId, command, stdoutCap, hostTimeoutMs, stdinFile);
            if (phase != null) {
                return phase;
            }
            // The container did not run it — it is gone, or unusable. Stop trusting it and redo the
            // command on the one-shot path, which is what the judge did before containers were reused.
            log.warn("sandbox container for {} did not run a command; falling back to one-shot", runId);
            removeContainer(runId);
        }
        DockerExecUtil.ExecOutcome o =
                docker.exec(oneShotArgv(runId, limits.memMb(), command, stdinFile),
                        stdoutCap, hostTimeoutMs);
        return new Phase(o.exitCode(), o.stdout(), o.stderr(), o.truncated(),
                o.hostTimedOut(), o.hostWallMs());
    }

    /**
     * Executes inside the shared container. Returns null when the command provably did not run, so
     * the caller can redo it on the one-shot path; a host timeout is a real result, not a failure.
     */
    private Phase execInContainer(String runId, List<String> command,
                                  int stdoutCap, long hostTimeoutMs, String stdinFile) {
        Path exitFile = runDir(runId).resolve(EXIT_FILE);
        try {
            Files.deleteIfExists(exitFile);
        } catch (IOException e) {
            return null;
        }
        List<String> argv = new ArrayList<>(List.of(
                "docker", "exec", "-w", props.workDir() + "/" + runId, containerName(runId),
                "sh", "-c", shellScript(command, stdinFile)));
        DockerExecUtil.ExecOutcome o = docker.exec(argv, stdoutCap, hostTimeoutMs);

        if (o.hostTimedOut()) {
            // The program outran even the host ceiling. That IS the verdict (TLE), but whatever is
            // still running in there is not worth sharing with the remaining cases.
            removeContainer(runId);
            return new Phase(null, o.stdout(), o.stderr(), o.truncated(), true, o.hostWallMs());
        }
        Integer exit = readExitCode(exitFile);
        if (exit == null) {
            return null; // the script never reached `echo $?` — the container is gone
        }
        return new Phase(exit, o.stdout(), o.stderr(), o.truncated(), false, o.hostWallMs());
    }

    private static final String EXIT_FILE = "exit.txt";

    /**
     * {@code <cmd> [< stdin]; echo $? > exit.txt} — the trailing write is what tells the backend the
     * command really ran (see the class javadoc). Written with {@code printf %s} quoting so a
     * command fragment can never be re-split by the shell.
     */
    static String shellScript(List<String> command, String stdinFile) {
        StringBuilder sb = new StringBuilder();
        for (String part : command) {
            sb.append(shellQuote(part)).append(' ');
        }
        if (stdinFile != null) {
            sb.append("< ").append(shellQuote(stdinFile)).append(' ');
        }
        sb.append("; echo $? > ").append(EXIT_FILE);
        return sb.toString();
    }

    /** Single-quote for /bin/sh, escaping embedded single quotes. */
    static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private Integer readExitCode(Path exitFile) {
        try {
            if (!Files.exists(exitFile)) {
                return null;
            }
            String text = Files.readString(exitFile).trim();
            return text.isEmpty() ? null : Integer.valueOf(text);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    // --- container lifecycle -------------------------------------------------------------------

    private String containerName(String runId) {
        return "praetor-run-" + runId;
    }

    /**
     * Starts the submission's sandbox container. Best-effort: if it will not start, the run id is
     * simply never marked live and every phase takes the one-shot path.
     */
    private void startContainer(String runId, RunLimits limits) {
        List<String> argv = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-d",
                "--name", containerName(runId),
                "--network", "none",
                "--memory", limits.memMb() + "m",
                "--cpus", "1",
                "--pids-limit", String.valueOf(limits.pidsMax()),
                "-v", props.volumeName() + ":" + props.workDir(),
                "-w", props.workDir() + "/" + runId,
                props.image(),
                "sleep", "infinity"));
        DockerExecUtil.ExecOutcome o = docker.exec(argv, 8 * 1024, 30_000);
        if (!o.hostTimedOut() && o.exitCode() != null && o.exitCode() == 0) {
            liveContainers.add(runId);
        } else {
            log.warn("could not start sandbox container for {} ({}); using one-shot containers: {}",
                    runId, o.exitCode(), o.stderr());
        }
    }

    private void removeContainer(String runId) {
        if (!liveContainers.remove(runId)) {
            return;
        }
        try {
            docker.exec(List.of("docker", "rm", "-f", containerName(runId)), 8 * 1024, 15_000);
        } catch (SandboxException e) {
            log.warn("could not remove sandbox container for {}: {}", runId, e.getMessage());
        }
    }

    // --- one-shot path -------------------------------------------------------------------------

    /**
     * The original argv: a locked-down {@code docker run --rm} for a single command. {@code memMb}
     * is the container memory cap — the run phase passes the per-language-scaled
     * {@code limits.memMb()} so an interpreted runtime isn't OOM-killed below its MLE threshold.
     */
    private List<String> oneShotArgv(String runId, int memMb, List<String> command, String stdinFile) {
        List<String> c = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", memMb + "m",
                "--cpus", "1",
                "--pids-limit", String.valueOf(props.pidsMax()),
                "-v", props.volumeName() + ":" + props.workDir(),
                "-w", props.workDir() + "/" + runId,
                props.image()));
        if (stdinFile == null) {
            c.addAll(command);
        } else {
            // stdin redirection needs a shell; without one the '<' would be an argument.
            StringBuilder sb = new StringBuilder();
            for (String part : command) {
                sb.append(shellQuote(part)).append(' ');
            }
            sb.append("< ").append(shellQuote(stdinFile));
            c.addAll(List.of("sh", "-c", sb.toString()));
        }
        return c;
    }

    private Path runDir(String runId) {
        return Path.of(props.workDir(), runId);
    }

    private record Metrics(int wallMs, int memKb) {
    }

    /** Parses {@code time.txt} (GNU time -v); returns -1 for fields it can't read. */
    private Metrics readMetrics(Path dir) {
        Path f = dir.resolve("time.txt");
        if (!Files.exists(f)) {
            return new Metrics(-1, -1);
        }
        String text;
        try {
            text = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Metrics(-1, -1);
        }
        int wall = -1;
        int mem = -1;
        Matcher em = ELAPSED.matcher(text);
        if (em.find()) {
            wall = parseElapsedMs(em.group(1));
        }
        Matcher mm = MAXRSS.matcher(text);
        if (mm.find()) {
            mem = Integer.parseInt(mm.group(1));
        }
        return new Metrics(wall, mem);
    }

    /** {@code "0:00.01"} / {@code "1:02.5"} / {@code "1:02:03"} → milliseconds; -1 if unparseable. */
    static int parseElapsedMs(String s) {
        String[] parts = s.split(":");
        double sec;
        try {
            if (parts.length == 3) {
                sec = Integer.parseInt(parts[0]) * 3600.0
                        + Integer.parseInt(parts[1]) * 60.0
                        + Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                sec = Integer.parseInt(parts[0]) * 60.0 + Double.parseDouble(parts[1]);
            } else {
                sec = Double.parseDouble(parts[0]);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return (int) Math.round(sec * 1000);
    }
}
