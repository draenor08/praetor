package com.praetor.identity.dto;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntry> content,
        Integer page,
        Integer size,
        Long totalElements) {
}
