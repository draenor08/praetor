package com.praetor.contest.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/contests/{id}/proposals — a setter offering one of their drafts. */
public record ProposeRequest(
        @NotNull Long problemId,
        String note) {
}
