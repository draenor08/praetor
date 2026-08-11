package com.praetor.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongPasswordIs401NotAnInternalError() {
        var response = handler.handleAuthenticationException(
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", 401);
    }

    @Test
    void theFailureMessageRevealsNothingAboutTheAccount() {
        // Identical body whether the username exists or not, so responses cannot enumerate users,
        // and no server-side exception text is echoed back.
        var wrongPassword = handler.handleAuthenticationException(
                new BadCredentialsException("Bad credentials for user alice"));
        var otherFailure = handler.handleAuthenticationException(
                new DisabledException("User account is disabled"));

        assertThat(wrongPassword.getBody()).isEqualTo(otherFailure.getBody());
        assertThat(wrongPassword.getBody()).containsEntry("error", "invalid username or password");
        assertThat(wrongPassword.getBody().toString()).doesNotContain("alice");
    }

    @Test
    void unexpectedFailuresDoNotLeakInternals() {
        var response = handler.handleAllExceptions(
                new IllegalStateException("ERROR: relation \"users\" does not exist"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "internal server error");
        assertThat(response.getBody().toString()).doesNotContain("relation");
    }
}
