package com.praetor.contest.controller;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Package-scoped advice so contest 403/404/409 surface with their real status + the {@code
 * {error,status}} shape. Without it the shared {@code GlobalExceptionHandler} (LOWEST_PRECEDENCE)
 * flattens every {@code RuntimeException} — including {@code ResponseStatusException} — to 400.
 */
@RestControllerAdvice(basePackages = "com.praetor.contest")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContestExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "error" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason, "status", ex.getStatusCode().value()));
    }
}
