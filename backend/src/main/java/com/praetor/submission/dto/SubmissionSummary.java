package com.praetor.submission.dto;

import java.time.ZonedDateTime;

/**
 * A single row in the submission history page. This intentionally omits source code and per-testcase
 * details, since the list is a lightweight overview and the full submission body is available only on
 * GET /api/submissions/{id} for the owner or ADMIN.
 */
public record SubmissionSummary(
        Long id,
        String handle,
        String problemSlug,
        String language,
        String status,
        String verdict,
        Integer score,
        ZonedDateTime createdAt) {
}
