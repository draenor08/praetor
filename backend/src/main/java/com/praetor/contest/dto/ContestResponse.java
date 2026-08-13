package com.praetor.contest.dto;

import java.util.List;

/**
 * GET /api/contests/{id} — contest meta + its problem slots in display order.
 *
 * <p>{@code registered} answers "is the caller signed up for this contest" (false for an anonymous
 * reader), so the page can show a register prompt instead of a problem list. {@code problemsVisible}
 * says whether the slots carry their slug and title; see {@link ContestProblemSlot}.
 *
 * <p>{@code serverNow} is this response's server instant, for the same reason as on
 * {@link ContestSummary}: the embargo, the freeze and the publish sweep all key off the server clock,
 * so anything the page says about time has to be measured against that clock, not the browser's.
 */
public record ContestResponse(
        Long id,
        String title,
        String startsAt,
        String endsAt,
        int freezeMin,
        String scoring,
        boolean registered,
        boolean callsOpen,
        boolean problemsVisible,
        List<ContestProblemSlot> problems,
        String serverNow) {
}
