package com.praetor.submission.dto;

/** 202 Accepted body for POST /api/submissions — the submission is queued, not yet judged. */
public record SubmissionCreatedResponse(Long id, String status) {
}
