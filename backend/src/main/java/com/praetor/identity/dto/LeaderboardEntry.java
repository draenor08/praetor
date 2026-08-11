package com.praetor.identity.dto;

public record LeaderboardEntry(
        Long rank,
        String handle,
        Integer rating) {
}
