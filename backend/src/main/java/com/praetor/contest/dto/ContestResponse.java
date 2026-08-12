package com.praetor.contest.dto;

import java.util.List;

/**
 * GET /api/contests/{id} — contest meta + its problem slots in display order.
 *
 * <p>{@code registered} answers "is the caller signed up for this contest" (false for an anonymous
 * reader), so the page can show a register prompt instead of a problem list. {@code problemsVisible}
 * says whether the slots carry their slug and title; see {@link ContestProblemSlot}.
 */
public record ContestResponse(
        Long id,
        String title,
        String startsAt,
        String endsAt,
        int freezeMin,
        String scoring,
        boolean registered,
        boolean problemsVisible,
        List<ContestProblemSlot> problems) {
}
