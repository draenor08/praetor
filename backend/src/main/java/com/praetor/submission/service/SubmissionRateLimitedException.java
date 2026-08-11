package com.praetor.submission.service;

/**
 * Thrown when a user submits again inside the cooldown. Carries the remaining wait so the
 * response can send a {@code Retry-After} header and a {@code retryAfterSec} field — the UI
 * counts down with it instead of showing a bare failure.
 *
 * <p>A dedicated type rather than {@code ResponseStatusException} precisely because that wait
 * has to survive as data all the way to the handler.
 */
public class SubmissionRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public SubmissionRateLimitedException(long retryAfterSeconds) {
        super("too many submissions — retry in " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
