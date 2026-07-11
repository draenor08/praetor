package com.praetor.submission;

/**
 * Submission pipeline states (the {@code submissions.status} vocabulary). Stored as Strings to
 * match the schema's {@code CHECK} constraint; centralized here so a typo can't silently strand a
 * submission. Distinct from {@link Verdict} — status is where the judge is, verdict is the outcome.
 */
public final class SubmissionStatus {

    public static final String QUEUED = "QUEUED";
    public static final String JUDGING = "JUDGING";
    public static final String DONE = "DONE";
    public static final String ERROR = "ERROR";

    private SubmissionStatus() {
    }
}
