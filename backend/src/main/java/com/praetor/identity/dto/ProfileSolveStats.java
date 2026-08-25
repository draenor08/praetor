package com.praetor.identity.dto;

import java.util.Map;

/**
 * Per-user solve statistics for the profile page (FR-25). Shape is fixed by
 * {@code docs/api-contracts.md}.
 *
 * @param solved    distinct problems with at least one accepted submission
 * @param attempted judged submissions — the tally in {@code byVerdict} sums to this, so anything
 *                  still queued or judging is not counted
 * @param accuracy  accepted submissions over {@code attempted}, in {@code [0, 1]}; zero when
 *                  nothing has been judged yet. Note this counts <i>submissions</i>, not problems:
 *                  five accepted attempts at one problem out of ten submissions is 0.5, not 0.1.
 * @param byVerdict verdict → how many submissions carried it, only for verdicts that occurred
 */
public record ProfileSolveStats(
        long solved,
        long attempted,
        double accuracy,
        Map<String, Long> byVerdict) {
}
