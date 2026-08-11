package com.praetor.identity.dto;

import java.util.List;

public record RatingResponse(
        Integer rating,
        Long rank,
        List<RatingHistoryResponse> history) {
}
