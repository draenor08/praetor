package com.praetor.submission.engine;

/**
 * Outcome of running the compiled program against one test case.
 *
 * @param exitCode  process exit code; null if killed before exit
 * @param stdout    captured stdout (bounded — see {@code truncated})
 * @param wallMs    measured program wall time in ms
 * @param memKb     peak RSS in kb (from {@code /usr/bin/time %M}); may be null if unmeasured
 * @param timedOut  killed at the hard wall ceiling
 * @param truncated stdout exceeded the byte cap and the run was killed (output-flood)
 */
public record RunResult(Integer exitCode, String stdout, int wallMs, Integer memKb,
                        boolean timedOut, boolean truncated) {
}
