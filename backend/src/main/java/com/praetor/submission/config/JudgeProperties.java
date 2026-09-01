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
 *
 * <p>{@code workDir}/{@code volumeName} are deployment constants tied to docker-compose: the
 * shared named volume ({@code volumeName}) is mounted into the backend at {@code workDir} AND
 * passed to each sibling judge {@code docker run -v <volumeName>:<workDir>}, so both see the
 * same bytes (named volumes resolve by name on the daemon — no host path). {@code oomInspect}
 * off = the cheap exit-137 heuristic; on = authoritative {@code docker inspect .State.OOMKilled}
 * (drops {@code --rm}).
 *
 * <p><b>{@code reuseContainer}</b> on = one sandbox container per SUBMISSION, with each test case
 * run through {@code docker exec}; off = the original container per TEST CASE. On is much faster
 * (a container launch measured ~890 ms against ~60 ms for an exec, so a 10-case problem went from
 * ~8.9 s to well under 2 s) and is the default. The switch exists because the judge is the one
 * subsystem where a wrong answer is worse than a slow one: turning it off restores the older,
 * simpler path without a rebuild. The runner also falls back to that path on its own whenever the
 * shared container turns out not to have run a case.
 */
@ConfigurationProperties(prefix = "praetor.judge")
@Validated
public record JudgeProperties(
        @NotBlank String image,
        @Positive int cpuSeconds,
        @Positive int memMb,
        @Positive int pidsMax,
        @Positive int workers,
        @NotBlank String workDir,
        @NotBlank String volumeName,
        boolean oomInspect,
        boolean reuseContainer) {
}
