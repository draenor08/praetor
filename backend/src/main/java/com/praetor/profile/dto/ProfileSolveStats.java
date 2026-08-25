package com.praetor.profile.dto;

/** Basic per-user solve statistics exposed on the profile page. */
public record ProfileSolveStats(
        String username,
        long solvedCount,
        long submissionCount,
        double acceptanceRate
) {
}
