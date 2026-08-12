package com.praetor.contest.dto;

/** A setter's offer of a problem for a contest, with the problem it names. */
public record ProposalDto(
        Long id,
        Long contestId,
        Long problemId,
        String slug,
        String title,
        Integer difficulty,
        String judgeMode,
        String proposedBy,
        String status,
        String note,
        long testCases,
        String createdAt) {
}
