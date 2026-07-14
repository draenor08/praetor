package com.praetor.contest.dto;

import java.util.List;

/**
 * The full ICPC standings board — FROZEN contract (see docs/api-contracts.md). Identical shape for
 * the {@code GET /api/contests/{id}/standings} snapshot and each {@code /topic/contest/{id}/standings}
 * WS push. Every push is a whole board (full recompute), not a delta, so a client that misses one
 * push self-heals on the next.
 *
 * @param frozen true iff a freeze window is active right now (independent of the viewer — a
 *               privileged viewer still sees {@code frozen:true} but with no frozen cells).
 * @param updatedAt ISO-8601 instant the board was computed.
 */
public record StandingsResponse(
        Long contestId,
        boolean frozen,
        String updatedAt,
        List<StandingsRow> rows) {
}
