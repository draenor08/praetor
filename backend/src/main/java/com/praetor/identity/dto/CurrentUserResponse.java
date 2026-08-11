package com.praetor.identity.dto;

/**
 * The signed-in user, as {@code GET /api/users/me} returns it.
 *
 * <p>Field names must stay identical to {@link UserResponse} (what login and register
 * return): the frontend caches whichever of the two it saw last under one key, so a
 * divergent name here silently blanks the topbar handle and the standings self-row.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String role,
        Integer rating) {
}
