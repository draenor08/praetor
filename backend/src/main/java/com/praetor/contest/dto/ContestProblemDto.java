package com.praetor.contest.dto;

/** A contest's problem slot in GET responses (label trimmed, in display order). */
public record ContestProblemDto(String label, int ord, Long problemId) {
}
