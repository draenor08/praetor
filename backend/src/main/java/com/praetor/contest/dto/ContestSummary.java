package com.praetor.contest.dto;

/** GET /api/contests — one row of the contest list (meta only, no problem set). */
public record ContestSummary(
        Long id,
        String title,
        String startsAt,
        String endsAt,
        String scoring) {
}
