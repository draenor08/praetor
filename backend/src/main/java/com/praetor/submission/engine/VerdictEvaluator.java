package com.praetor.submission.engine;

import com.praetor.submission.Verdict;
import org.springframework.stereotype.Service;

/**
 * Maps a {@link RunResult} for one test case to a verdict. EXACT judge mode only in this slice
 * (TOKEN/FLOAT/SPECIAL are seams — the orchestrator rejects non-EXACT problems before we get here).
 *
 * <p>Ordering matters — the first matching rule wins:
 * truncated→RE, timedOut→TLE, over-memory→MLE, clean exit→(AC|WA|soft-TLE),
 * exit 137→(TLE if timed out else MLE), any other nonzero→RE.
 */
@Service
public class VerdictEvaluator {

    public String evaluate(RunResult rr, String expected, RunLimits limits) {
        if (rr.truncated()) {
            return Verdict.RE; // output-flood: treat as runtime error (no OLE verdict in scope)
        }
        if (rr.timedOut()) {
            return Verdict.TLE;
        }
        if (rr.memKb() != null && rr.memKb() >= limits.memLimitKb()) {
            return Verdict.MLE;
        }
        Integer exit = rr.exitCode();
        if (exit != null && exit == 0) {
            if (rr.wallMs() >= limits.softWallMs()) {
                return Verdict.TLE; // finished but over the per-problem time limit
            }
            return exactEqual(rr.stdout(), expected) ? Verdict.AC : Verdict.WA;
        }
        if (exit != null && exit == 137) {
            // 128+SIGKILL: OOM-killer vs timeout-escalated kill are both 137. If our own wall
            // timer fired it's TLE; otherwise the container OOMed → MLE. (PR-2 can disambiguate
            // authoritatively via `docker inspect .State.OOMKilled` behind praetor.judge.oom-inspect.)
            return rr.timedOut() ? Verdict.TLE : Verdict.MLE;
        }
        return Verdict.RE; // segfault (139), abort (134), div-by-zero (136), any nonzero
    }

    /**
     * EXACT comparison, tolerant of trailing whitespace: strip trailing whitespace per line and
     * drop trailing blank lines, then compare line-by-line. So {@code "5"} and {@code "5\n"} match.
     */
    boolean exactEqual(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].strip().isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(stripTrailing(lines[i]));
        }
        return sb.toString();
    }

    private String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
