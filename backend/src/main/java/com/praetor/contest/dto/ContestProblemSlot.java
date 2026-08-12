package com.praetor.contest.dto;

/**
 * A problem slot on the contest page. The label and order are always present — the standings board
 * needs its columns even for a spectator — but {@code slug} and {@code title} are null while the
 * problem is under contest embargo and the caller is neither staff nor a registered participant.
 * Withholding the identity server-side means a hidden statement cannot be reached by reading the
 * page's own payload.
 */
public record ContestProblemSlot(String label, int ord, Long problemId, String slug, String title) {

    /** The same slot with the problem's identity stripped, for a caller who may not see it yet. */
    public static ContestProblemSlot withheld(String label, int ord, Long problemId) {
        return new ContestProblemSlot(label, ord, problemId, null, null);
    }
}
