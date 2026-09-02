package com.praetor.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/submissions request body. {@code language} is validated against the supported
 * {@code Language} set in the service (CPP, PYTHON, JAVA).
 *
 * <p>There is deliberately no {@code contestId}. Whether a submission scores in a contest is
 * derived server-side from the caller's registrations and the contest window — see
 * {@code ContestAccessService.scoringContestFor}. It used to be a field here, and because the
 * frontend never sent it, every submission made in the browser was recorded as practice.
 */
public record SubmitRequest(
        @NotBlank String problemSlug,
        @NotBlank String language,
        @NotBlank @Size(max = 65_536) String sourceCode) {
}
