package com.praetor.contest.dto;

import java.util.List;

/**
 * One participant's row. {@code solved} = problems accepted; {@code penalty} = ICPC penalty
 * (Σ over solved problems of {@code solvedAtMin + 20 × rejectedAttemptsBeforeAC}). Rank ties (equal
 * solved AND penalty) share the same rank number.
 */
public record StandingsRow(
        int rank,
        String handle,
        int solved,
        int penalty,
        List<ProblemCell> problems) {
}
