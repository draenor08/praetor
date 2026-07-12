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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Real sandbox: compiles and runs submitted C++ in a locked-down sibling docker container
 * ({@code --rm --network none --memory --cpus --pids-limit}), sharing the per-run work dir with the
 * backend through the named volume {@code praetor_work} mounted at {@code workDir} in both.
 *
 * <p>Per-run isolation: everything lives under {@code <workDir>/<runId>/}; {@link #cleanup} removes
 * only that subtree, so concurrent workers never collide. Timing + peak memory are measured inside
 * the container by {@code /usr/bin/time -v} (written to {@code time.txt} on the shared volume), so
 * MLE is decided by measured RSS rather than the ambiguous exit-137 signal — which lets us keep
 * {@code --rm} and skip {@code docker inspect}.
 */
@Component
@Primary
public class DockerSandboxRunner implements SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxRunner.class);
    private static final int STDOUT_CAP = 256 * 1024; // output-flood cap → truncated → RE
    private static final Pattern ELAPSED =
            Pattern.compile("Elapsed \\(wall clock\\) time.*?:\\s*([0-9:.]+)");
    private static final Pattern MAXRSS =
            Pattern.compile("Maximum resident set size \\(kbytes\\):\\s*(\\d+)");

    private final JudgeProperties props;
    private final DockerExecUtil docker;

    public DockerSandboxRunner(JudgeProperties props, DockerExecUtil docker) {
        this.props = props;
        this.docker = docker;
    }

    @Override
    public CompileResult compile(String runId, String sourceCode) {
        Path dir = runDir(runId);
        try {
            Files.createDirectories(dir);
            // world-writable: the backend writes as root, the uid-1000 judge user writes prog/time.txt
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
            Files.writeString(dir.resolve("main.cpp"), sourceCode == null ? "" : sourceCode);
        } catch (IOException e) {
            throw new SandboxException("prepare work dir failed for " + runId, e);
        }
        List<String> cmd = dockerRun(runId,
                List.of("g++", "-O2", "-std=gnu++17", "-o", "prog", "main.cpp"));
        DockerExecUtil.ExecOutcome o = docker.exec(cmd, 64 * 1024, 30_000);
        if (o.hostTimedOut()) {
            return new CompileResult(false, "Compilation timed out.");
        }
        if (o.exitCode() != null && o.exitCode() == 0) {
            return new CompileResult(true, "");
        }
        return new CompileResult(false, o.stderr());
    }

    @Override
    public RunResult run(String runId, JudgeTestCase testCase, RunLimits limits) {
        Path dir = runDir(runId);
        try {
            Files.writeString(dir.resolve("input.txt"),
                    testCase.getInput() == null ? "" : testCase.getInput());
        } catch (IOException e) {
            throw new SandboxException("write input failed for " + runId, e);
        }
        int hardWallSec = Math.max(1, (int) Math.ceil(limits.hardWallMs() / 1000.0));
        // /usr/bin/time -v -o time.txt   timeout -s KILL <hard>s   ./prog < input.txt
        String script = "/usr/bin/time -v -o time.txt timeout -s KILL "
                + hardWallSec + "s ./prog < input.txt";
        List<String> cmd = dockerRun(runId, List.of("sh", "-c", script));
        long hostTimeout = limits.hardWallMs() + 15_000L; // slack above the container timer for startup
        DockerExecUtil.ExecOutcome o = docker.exec(cmd, STDOUT_CAP, hostTimeout);

        Metrics m = readMetrics(dir);
        int wallMs = (m.wallMs() >= 0) ? m.wallMs() : o.hostWallMs();
        Integer memKb = (m.memKb() >= 0) ? m.memKb() : null;
        Integer exit = o.hostTimedOut() ? null : o.exitCode();
        // timeout -s KILL exits 137, plain timeout exits 124; both may coincide with a real OOM 137,
        // so confirm a time-based kill via the measured wall clock.
        boolean timedOut = o.hostTimedOut()
                || (exit != null && (exit == 124
                    || (exit == 137 && wallMs >= 0.9 * limits.hardWallMs())));
        return new RunResult(exit, o.stdout(), wallMs, memKb, timedOut, o.truncated());
    }

    @Override
    public void cleanup(String runId) {
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

    private Path runDir(String runId) {
        return Path.of(props.workDir(), runId);
    }

    /** Builds the locked-down {@code docker run} argv for a command executed inside the run dir. */
    private List<String> dockerRun(String runId, List<String> containerCmd) {
        List<String> c = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", props.memMb() + "m",
                "--cpus", "1",
                "--pids-limit", String.valueOf(props.pidsMax()),
                "-v", props.volumeName() + ":" + props.workDir(),
                "-w", props.workDir() + "/" + runId,
                props.image()));
        c.addAll(containerCmd);
        return c;
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
