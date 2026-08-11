package com.praetor.identity.controller;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Honors {@link ResponseStatusException} status codes inside the identity module and emits the
 * contract error shape {@code {error, status}}.
 *
 * <p>Without this, the module's broad {@code RuntimeException → 400} advice swallows them:
 * an unknown handle on {@code /api/users/{handle}/rating} answered 400 instead of 404, and a
 * non-ADMIN hitting the rating-apply endpoint would get 400 instead of 403. Scoped to
 * {@code com.praetor.identity} at highest precedence — the same pattern the submission,
 * contest and problem modules already use, and it leaves the broad advice (which auth still
 * relies on for its {@code IllegalArgumentException}s) untouched.
 */
@RestControllerAdvice(basePackages = "com.praetor.identity")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "error" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason, "status", ex.getStatusCode().value()));
    }
}
