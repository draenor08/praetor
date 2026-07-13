package com.praetor.submission.engine;

import com.praetor.submission.Verdict;
import com.praetor.submission.engine.checker.Checker;
import org.springframework.stereotype.Service;

/**
 * Maps a {@link RunResult} for one test case to a verdict. The AC-vs-WA decision is delegated to the
 * per-problem {@link Checker} (EXACT/TOKEN/FLOAT); everything else is verdict-mode-independent.
 *
 * <p>Ordering matters — the first matching rule wins:
 * truncated→RE, timedOut→TLE, over-memory→MLE, clean exit→(AC|WA|soft-TLE),
 * exit 137→(TLE if timed out else MLE), any other nonzero→RE.
 */
@Service
public class VerdictEvaluator {

    public String evaluate(RunResult rr, String expected, RunLimits limits, Checker checker) {
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
            return checker.matches(rr.stdout(), expected) ? Verdict.AC : Verdict.WA;
        }
        if (exit != null && exit == 137) {
            // 128+SIGKILL: OOM-killer vs timeout-escalated kill are both 137. If our own wall
            // timer fired it's TLE; otherwise the container OOMed → MLE. (PR-2 can disambiguate
            // authoritatively via `docker inspect .State.OOMKilled` behind praetor.judge.oom-inspect.)
            return rr.timedOut() ? Verdict.TLE : Verdict.MLE;
        }
        return Verdict.RE; // segfault (139), abort (134), div-by-zero (136), any nonzero
    }
}
