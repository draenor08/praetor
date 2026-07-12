package com.praetor.problem.controller;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Honors {@link ResponseStatusException} status codes for the problem-read shim and emits the
 * contract error shape {@code {error, status}}. Scoped to {@code com.praetor.problem} and highest
 * precedence so it wins over the identity module's broad {@code RuntimeException→400} advice (which
 * would otherwise flatten a 404 for an unknown slug to 400) — without editing that teammate file.
 * Mirror of {@code SubmissionExceptionHandler}; retire with the rest of the shim.
 */
@RestControllerAdvice(basePackages = "com.praetor.problem")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "error" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason, "status", ex.getStatusCode().value()));
    }
}
