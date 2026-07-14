package com.praetor.contest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Dedicated higher-precedence {@link SecurityFilterChain} making contest READS public (contract:
 * GET /api/contests/** = auth "any", so anonymous spectators can watch standings). POST create/
 * register are NOT matched here → they fall through to the identity module's authenticated default
 * chain + the in-service ADMIN/USER gate. Kept SEPARATE (like {@code WsSecurityConfig}); does not
 * edit the teammate-owned SecurityConfig. Order 2 vs WsSecurityConfig's 1 — different matchers, no
 * overlap ({@code /ws/**} vs GET {@code /api/contests/**}).
 */
@Configuration
public class ContestWebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain contestReadSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/api/contests/**", HttpMethod.GET.name()))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
