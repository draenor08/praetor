package com.praetor.contest.dto;

/**
 * GET /api/contests — one row of the contest list (meta only, no problem set).
 *
 * <p>{@code serverNow} is this response's server instant. Every window decision in this system is
 * made against the server clock, so a client that wants to say "starts in 12 minutes" has to measure
 * against the same clock rather than the browser's — a laptop minutes out of sync would otherwise
 * count down to the wrong moment and disagree with the embargo it is describing.
 */
public record ContestSummary(
        Long id,
        String title,
        String startsAt,
        String endsAt,
        String scoring,
        /** Setters may propose problems for this contest. */
        boolean callsOpen,
        String serverNow) {
}
