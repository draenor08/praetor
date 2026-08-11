package com.praetor.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
public class IdentityReadSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain identityReadSecurityFilterChain(
            HttpSecurity http) throws Exception {

        OrRequestMatcher publicRatingReads =
                new OrRequestMatcher(
                        new AntPathRequestMatcher(
                                "/api/leaderboard",
                                HttpMethod.GET.name()),
                        new AntPathRequestMatcher(
                                "/api/users/*/rating",
                                HttpMethod.GET.name()));

        http
                .securityMatcher(publicRatingReads)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll());

        return http.build();
    }
}
