package com.praetor.submission.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerdictEvaluatorTest {

    private final VerdictEvaluator ev = new VerdictEvaluator();
    // soft time limit 1000ms, hard 2000ms, mem limit 262144 kb
    private final RunLimits limits = new RunLimits(1000, 2000, 256, 64, 262_144);

    private RunResult cleanExit(String stdout, int wallMs, Integer memKb) {
        return new RunResult(0, stdout, wallMs, memKb, false, false);
    }

    @Test
    void ac_on_exact_match() {
        assertThat(ev.evaluate(cleanExit("5\n", 10, 2000), "5", limits)).isEqualTo("AC");
    }

    @Test
    void ac_tolerates_trailing_whitespace_and_blank_lines() {
        assertThat(ev.evaluate(cleanExit("5   \n\n", 10, 2000), "5", limits)).isEqualTo("AC");
    }

    @Test
    void wa_on_mismatch() {
        assertThat(ev.evaluate(cleanExit("6", 10, 2000), "5", limits)).isEqualTo("WA");
    }

    @Test
    void tle_when_timed_out() {
        assertThat(ev.evaluate(new RunResult(null, "", 2000, null, true, false), "5", limits))
                .isEqualTo("TLE");
    }

    @Test
    void tle_when_finished_over_soft_limit() {
        assertThat(ev.evaluate(cleanExit("5", 1500, 2000), "5", limits)).isEqualTo("TLE");
    }

    @Test
    void mle_when_peak_mem_exceeds_limit() {
        assertThat(ev.evaluate(cleanExit("5", 10, 300_000), "5", limits)).isEqualTo("MLE");
    }

    @Test
    void re_on_nonzero_exit() {
        assertThat(ev.evaluate(new RunResult(139, "", 10, 2000, false, false), "5", limits))
                .isEqualTo("RE");
    }

    @Test
    void re_on_output_flood_truncation() {
        assertThat(ev.evaluate(new RunResult(0, "huge", 10, 2000, false, true), "5", limits))
                .isEqualTo("RE");
    }

    @Test
    void mle_on_137_without_timeout() {
        assertThat(ev.evaluate(new RunResult(137, "", 10, null, false, false), "5", limits))
                .isEqualTo("MLE");
    }
}
