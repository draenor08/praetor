package com.praetor.problem.dto;

/** One row of GET /api/problems (the problem list). */
public record ProblemSummary(String slug, String title, Integer difficulty, String judgeMode) {
}
