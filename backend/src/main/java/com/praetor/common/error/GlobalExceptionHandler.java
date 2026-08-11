package com.praetor.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Failed login → 401, not 500.
     *
     * <p>Spring's {@code AuthenticationManager} throws {@link BadCredentialsException} for a wrong
     * password. With no handler for it, it fell through to the catch-all below and answered
     * {@code 500} with the raw exception text — the wrong status for an expected outcome, and a
     * detail leak. The message here is deliberately identical whether the username exists or not,
     * so the response cannot be used to enumerate accounts.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getClass().getSimpleName());
        Map<String, Object> error = new HashMap<>();
        error.put("error", "invalid username or password");
        error.put("status", HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Anything genuinely unexpected. Logged in full for debugging, but the response body says
     * nothing about internals — {@code ex.getMessage()} on an arbitrary exception can carry SQL,
     * file paths or class names.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", "internal server error");
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeExceptions(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        // For security or conflict exceptions, one might use a custom exception class. 
        // We will keep it simple and return 400.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
