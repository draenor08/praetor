package com.praetor.identity.dto;

public record CurrentUserResponse(
        Long id,
        String handle,
        String email,
        String role,
        Integer rating) {
}
