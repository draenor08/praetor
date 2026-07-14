package com.praetor.contest.dto;

import java.util.List;

/** GET /api/contests/{id} — contest meta + its problems in display order. */
public record ContestResponse(
        Long id,
        String title,
        String startsAt,
        String endsAt,
        int freezeMin,
        String scoring,
        List<ContestProblemDto> problems) {
}
