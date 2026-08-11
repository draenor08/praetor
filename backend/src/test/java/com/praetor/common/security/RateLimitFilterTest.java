package com.praetor.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void secondSubmissionFromSameUserIsRateLimited() throws Exception {

        RateLimitFilter filter = new RateLimitFilter(10);

        authenticate("alice");

        MockHttpServletResponse firstResponse =
                new MockHttpServletResponse();

        filter.doFilterInternal(
                submissionRequest(),
                firstResponse,
                new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());

        MockHttpServletResponse secondResponse =
                new MockHttpServletResponse();

        filter.doFilterInternal(
                submissionRequest(),
                secondResponse,
                new MockFilterChain());

        assertEquals(429, secondResponse.getStatus());

        assertTrue(
                secondResponse.getContentAsString()
                        .contains("\"error\":\"rate limited\""));

        assertTrue(
                secondResponse.getContentAsString()
                        .contains("\"retryAfterSec\":"));

        assertTrue(
                secondResponse.containsHeader("Retry-After"));
    }

    @Test
    void differentUsersHaveSeparateRateLimits() throws Exception {

        RateLimitFilter filter = new RateLimitFilter(10);

        authenticate("alice");

        MockHttpServletResponse aliceResponse =
                new MockHttpServletResponse();

        filter.doFilterInternal(
                submissionRequest(),
                aliceResponse,
                new MockFilterChain());

        assertEquals(200, aliceResponse.getStatus());

        authenticate("bob");

        MockHttpServletResponse bobResponse =
                new MockHttpServletResponse();

        filter.doFilterInternal(
                submissionRequest(),
                bobResponse,
                new MockFilterChain());

        assertEquals(200, bobResponse.getStatus());
    }

    private MockHttpServletRequest submissionRequest() {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setMethod("POST");
        request.setRequestURI("/api/submissions");

        return request;
    }

    private void authenticate(String username) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of());

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }
}
