package com.praetor.submission;

/**
 * Judge outcomes (the {@code submissions.verdict} / {@code submission_results.verdict} vocabulary).
 * Stored as Strings to match the schema's {@code CHECK} constraint. Only meaningful once the
 * submission's {@link SubmissionStatus} is {@code DONE}.
 */
public final class Verdict {

    public static final String AC = "AC";
    public static final String WA = "WA";
    public static final String TLE = "TLE";
    public static final String MLE = "MLE";
    public static final String RE = "RE";
    public static final String CE = "CE";
    public static final String PE = "PE";

    private Verdict() {
    }
}
