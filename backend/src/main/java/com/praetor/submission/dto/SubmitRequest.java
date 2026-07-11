package com.praetor.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/submissions request body. {@code contestId} is optional (null = practice).
 * {@code language} is validated for the supported set in the service (CPP only in this slice).
 */
public record SubmitRequest(
        @NotBlank String problemSlug,
        Long contestId,
        @NotBlank String language,
        @NotBlank @Size(max = 65_536) String sourceCode) {
}
