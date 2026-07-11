package com.praetor.submission.dto;

/**
 * WebSocket payload pushed to {@code /user/queue/submission/{id}} on each status change.
 * {@code verdict} is null until the submission reaches DONE.
 */
public record SubmissionStatusEvent(Long id, String status, String verdict) {
}
