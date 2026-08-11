package com.praetor.problem.dto;

/**
 * One row of the setter workspace list: the problem, plus everything the UI needs to decide which
 * actions to offer for it.
 *
 * <p>{@code deletable} and {@code lockReason} are computed server-side so the same rule decides
 * what the button says and what the endpoint allows — a UI that works them out itself drifts from
 * the guard and starts offering actions that 409.
 */
public record ManagedProblemResponse(
        String slug,
        String title,
        int difficulty,
        String judgeMode,
        boolean archived,
        long testCases,
        long submissions,
        long contests,
        boolean inLiveContest,
        boolean deletable,
        String lockReason) {
}
