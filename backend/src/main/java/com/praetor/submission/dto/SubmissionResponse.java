package com.praetor.submission.dto;

import java.util.List;

/**
 * GET /api/submissions/{id} response. {@code handle} is the owner's username (the User entity has
 * no {@code handle} field — mapped from {@code username}). {@code verdict}/{@code timeMs}/
 * {@code memKb} are null and {@code results} empty until the submission is judged.
 */
public record SubmissionResponse(
        Long id,
        String handle,
        String problemSlug,
        String language,
        String status,
        String verdict,
        Integer timeMs,
        Integer memKb,
        String compileLog,
        String createdAt,
        List<ResultResponse> results) {
}
