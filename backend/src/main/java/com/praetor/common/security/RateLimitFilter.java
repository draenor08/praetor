package com.praetor.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Long> lastSubmissionByUser =
            new ConcurrentHashMap<>();

    private final long limitMillis;

    public RateLimitFilter(
            @Value("${praetor.rate-limit.submission-seconds:10}")
            long limitSeconds) {

        this.limitMillis = limitSeconds * 1000L;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // FR-26 applies only to POST /api/submissions
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/submissions".equals(request.getRequestURI())) {

            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = authentication.getName();
        long now = System.currentTimeMillis();

        synchronized (lastSubmissionByUser) {

            Long lastSubmission =
                    lastSubmissionByUser.get(username);

            if (lastSubmission != null) {

                long elapsed = now - lastSubmission;

                if (elapsed < limitMillis) {

                    long retryAfterSec =
                            (limitMillis - elapsed + 999) / 1000;

                    response.setStatus(
                            HttpStatus.TOO_MANY_REQUESTS.value());

                    response.setContentType(
                            MediaType.APPLICATION_JSON_VALUE);

                    response.setCharacterEncoding("UTF-8");

                    response.setHeader(
                            "Retry-After",
                            String.valueOf(retryAfterSec));

                    response.getWriter().write(
                            "{\"error\":\"rate limited\","
                                    + "\"retryAfterSec\":"
                                    + retryAfterSec
                                    + "}");

                    return;
                }
            }

            lastSubmissionByUser.put(username, now);
        }

        filterChain.doFilter(request, response);
    }
}