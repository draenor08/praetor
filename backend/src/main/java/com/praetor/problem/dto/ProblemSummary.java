package com.praetor.problem.dto;

import java.util.List;

/** One row of GET /api/problems (the problem list). {@code tags} is empty, never null. */
public record ProblemSummary(String slug, String title, Integer difficulty, String judgeMode,
                             List<String> tags) {
}
