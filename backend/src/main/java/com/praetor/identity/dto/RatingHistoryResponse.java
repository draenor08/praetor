package com.praetor.identity.dto;

public record RatingHistoryResponse(
        Long contestId,
        Integer before,
        Integer after,
        String at) {
}
