package com.praetor.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT signing config, bound from {@code praetor.jwt.*} (bridged from {@code JWT_SECRET} /
 * {@code JWT_EXPIRY_MIN} in {@code application.yml}).
 *
 * <p>Mirrors the {@code JudgeProperties} convention: a validated record so a bad value fails at
 * boot with a named field instead of at the first login.
 *
 * <p>{@code secret} must be at least 32 characters. HS256 keys are 256-bit, and
 * {@code Keys.hmacShaKeyFor} throws on anything shorter — as a runtime exception, on the first
 * token, which is a miserable way to find out.
 *
 * <p>There is no default secret. Previously the key was a {@code private static final String} in
 * {@code JwtService}, so the {@code JWT_SECRET} that docker-compose passes was silently ignored and
 * every deployment signed with the same value — which now sits in the public git history. A
 * fallback here would recreate exactly that, so the app refuses to start until the environment
 * supplies one. Expiry was likewise hardcoded to 24h, ignoring {@code JWT_EXPIRY_MIN}.
 */
@ConfigurationProperties(prefix = "praetor.jwt")
@Validated
public record JwtProperties(

        @NotBlank(message = "must be set — put JWT_SECRET in your .env "
                + "(generate one with: openssl rand -hex 32). There is no default on purpose.")
        @Size(min = 32, message = "must be at least 32 characters (HS256 needs a 256-bit key)")
        String secret,

        @Positive
        int expiryMin) {
}
