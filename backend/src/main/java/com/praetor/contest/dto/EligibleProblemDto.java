package com.praetor.contest.dto;

/** A draft problem a contest may still use. */
public record EligibleProblemDto(
        Long problemId,
        String slug,
        String title,
        Integer difficulty,
        String judgeMode,
        String author,
        long testCases) {
}
