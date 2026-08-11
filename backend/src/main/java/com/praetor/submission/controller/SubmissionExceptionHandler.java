package com.praetor.submission.controller;

import com.praetor.submission.service.SubmissionRateLimitedException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Honors {@link ResponseStatusException} status codes for this slice's controllers and emits the
 * contract error shape {@code {error, status}}. Scoped to {@code com.praetor.submission} and set to
 * highest precedence so it wins over the identity module's broad {@code RuntimeException→400}
 * advice (which would otherwise flatten our 404/403 to 400) — without editing that teammate file.
 */
@RestControllerAdvice(basePackages = "com.praetor.submission")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubmissionExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "error" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason, "status", ex.getStatusCode().value()));
    }

    /**
     * 429 for the submission cooldown, carrying the remaining wait twice: {@code Retry-After}
     * for anything speaking plain HTTP, and {@code retryAfterSec} in the body for the UI, which
     * counts it down and re-enables Submit on its own.
     */
    @ExceptionHandler(SubmissionRateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handle(SubmissionRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of(
                        "error", "too many submissions — wait before submitting again",
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "retryAfterSec", ex.getRetryAfterSeconds()));
    }
}
