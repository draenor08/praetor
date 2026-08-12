package com.praetor.contest.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/contests/{id}/calls — open or close the contest to setter proposals. */
public record CallsOpenRequest(
        @NotNull Boolean open) {
}
