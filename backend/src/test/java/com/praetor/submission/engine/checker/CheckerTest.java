package com.praetor.submission.engine.checker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CheckerTest {

    // ---- EXACT ----
    @Test
    void exact_tolerates_trailing_whitespace_and_blank_lines() {
        Checker c = new ExactChecker();
        assertThat(c.matches("5\n", "5")).isTrue();
        assertThat(c.matches("5   \n\n", "5")).isTrue();
    }

    @Test
    void exact_is_strict_about_internal_spacing_and_mismatch() {
        Checker c = new ExactChecker();
        assertThat(c.matches("1  2", "1 2")).isFalse(); // internal spacing significant
        assertThat(c.matches("6", "5")).isFalse();
    }

    // ---- TOKEN ----
    @Test
    void token_ignores_whitespace_layout() {
        Checker c = new TokenChecker();
        assertThat(c.matches("1 2 3", "1\n2\n3")).isTrue();
        assertThat(c.matches("  1   2\t3 \n", "1 2 3")).isTrue();
    }

    @Test
    void token_rejects_wrong_tokens_or_count() {
        Checker c = new TokenChecker();
        assertThat(c.matches("1 2", "1 2 3")).isFalse();
        assertThat(c.matches("1 2 4", "1 2 3")).isFalse();
    }

    // ---- FLOAT ----
    @Test
    void float_accepts_within_epsilon() {
        Checker c = new FloatChecker(1e-6);
        assertThat(c.matches("3.1415926", "3.14159265")).isTrue();       // diff ~5e-8
        assertThat(c.matches("1.0 2.0000001", "1.0 2.0")).isTrue();      // per-token within eps
    }

    @Test
    void float_rejects_outside_epsilon_and_uses_exact_for_nonnumeric() {
        Checker c = new FloatChecker(1e-6);
        assertThat(c.matches("3.15", "3.14")).isFalse();
        assertThat(c.matches("yes", "no")).isFalse();   // non-numeric → exact compare
        assertThat(c.matches("yes", "yes")).isTrue();
        assertThat(c.matches("1.0", "1.0 2.0")).isFalse(); // token count mismatch
    }
}
