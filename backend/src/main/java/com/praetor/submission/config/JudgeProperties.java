package com.praetor.submission.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Judge sandbox tunables, bound from {@code praetor.judge.*} — which application.yml
 * bridges from the {@code SANDBOX_*} / {@code JUDGE_WORKERS} env vars. Validated at
 * startup: a bad value fails the boot, not the first submission.
 *
 * <p><b>{@code cpuSeconds}</b> is the hard WALL-TIME ceiling per run, in seconds. The
 * per-problem time limit (from the {@code problems} table) must be ≤ this. It is NOT
 * the docker {@code --cpus} core count — that stays a code constant in the Step-3
 * {@code SandboxRunner}, alongside {@code --network none} / {@code --rm}.
 *
 * <p>Owned by the judging engine (the sole consumer); lives in {@code submission}, not
 * {@code common}.
 */
@ConfigurationProperties(prefix = "praetor.judge")
@Validated
public record JudgeProperties(
        @NotBlank String image,
        @Positive int cpuSeconds,
        @Positive int memMb,
        @Positive int pidsMax,
        @Positive int workers) {
}
