package com.praetor.contest.dto;

/** POST /api/contests/{id}/register body. virtual=true (upsolve) is rejected — FR-22 deferred. */
public record RegisterRequest(boolean virtual) {
}
