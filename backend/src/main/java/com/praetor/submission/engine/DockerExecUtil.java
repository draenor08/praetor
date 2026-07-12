package com.praetor.submission.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link ProcessBuilder} for launching {@code docker run} from the backend.
 * Captures stdout (byte-capped → {@code truncated}) and stderr on their own threads (so a chatty
 * child can't deadlock us), and enforces a host-side wall timeout as a safety net ABOVE the
 * container's own {@code timeout}. Never blocks forever on a hung child.
 */
@Component
public class DockerExecUtil {

    /**
     * @param command        full argv (e.g. {@code docker run ...})
     * @param stdoutCapBytes max stdout bytes to keep; beyond this the child is killed and
     *                       {@code truncated} is set
     * @param hostTimeoutMs  host-side kill ceiling; the container self-limits well below this
     */
    public ExecOutcome exec(List<String> command, int stdoutCapBytes, long hostTimeoutMs) {
        Process p;
        try {
            p = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            throw new SandboxException("failed to start: " + String.join(" ", command), e);
        }
        // The container reads its stdin from a file (input.txt) inside the volume, so we never
        // feed the process stdin — close it immediately.
        try {
            p.getOutputStream().close();
        } catch (IOException ignored) {
            // already closed
        }

        StreamPump out = new StreamPump(p.getInputStream(), stdoutCapBytes);
        StreamPump err = new StreamPump(p.getErrorStream(), 64 * 1024);
        Thread tOut = new Thread(out, "docker-stdout");
        Thread tErr = new Thread(err, "docker-stderr");
        tOut.start();
        tErr.start();

        long start = System.nanoTime();
        boolean finished;
        try {
            finished = p.waitFor(hostTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new SandboxException("interrupted waiting for " + String.join(" ", command), e);
        }
        boolean hostTimedOut = false;
        if (!finished) {
            hostTimedOut = true;
            p.destroyForcibly();
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int hostWallMs = (int) ((System.nanoTime() - start) / 1_000_000);
        joinQuietly(tOut);
        joinQuietly(tErr);

        Integer exit = finished ? p.exitValue() : null;
        return new ExecOutcome(exit, out.text(), err.text(), out.truncated(), hostTimedOut, hostWallMs);
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Outcome of one process launch. {@code exitCode} is null if the host timeout killed it. */
    public record ExecOutcome(Integer exitCode, String stdout, String stderr,
                              boolean truncated, boolean hostTimedOut, int hostWallMs) {
    }

    /** Reads a stream into a byte-capped buffer on its own thread; drains the rest to avoid blocking. */
    private static final class StreamPump implements Runnable {
        private final InputStream in;
        private final int cap;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean truncated;

        StreamPump(InputStream in, int cap) {
            this.in = in;
            this.cap = cap;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            int n;
            try {
                while ((n = in.read(chunk)) != -1) {
                    int room = cap - buf.size();
                    if (room <= 0) {
                        truncated = true;
                        continue; // keep draining so the child's write() doesn't block
                    }
                    buf.write(chunk, 0, Math.min(n, room));
                    if (n > room) {
                        truncated = true;
                    }
                }
            } catch (IOException e) {
                // stream closed when the child was killed — expected
            }
        }

        boolean truncated() {
            return truncated;
        }

        String text() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
