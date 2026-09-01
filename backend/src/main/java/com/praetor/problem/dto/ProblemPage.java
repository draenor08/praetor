package com.praetor.problem.dto;

import java.util.List;

/**
 * One page of GET /api/problems. Mirrors {@code SubmissionPage} so the two list endpoints answer in
 * the same shape rather than each inventing one.
 */
public record ProblemPage(
        List<ProblemSummary> content,
        Integer page,
        Integer size,
        Long totalElements) {
}
