package com.praetor.common.event;

/**
 * Published by the submission slice AFTER a contest submission's verdict is committed (post-commit,
 * so a listener that re-reads the DB sees the final row). The contest slice listens and recomputes
 * that contest's standings. Lives in {@code common} so neither slice depends on the other's Java
 * types — the only coupling is this neutral fact.
 *
 * @param contestId the contest whose board must be recomputed (never null — practice submissions
 *                  don't publish this event).
 */
public record ContestSubmissionJudgedEvent(Long contestId) {
}
