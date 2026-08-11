package com.praetor.problem.dto;

/**
 * Why a problem can or cannot be hard-deleted. The setter UI reads this before drawing the
 * button, so a setter is never offered a Delete that is going to come back 409 — when
 * {@code deletable} is false it offers Archive and shows {@code reason} instead.
 */
public record ProblemUsageResponse(
        String slug,
        boolean deletable,
        boolean archived,
        boolean inLiveContest,
        long submissions,
        long clarifications,
        String contestTitle,
        String reason) {
}
