package com.praetor.problem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
public class ProblemWebSecurityConfig {

    @Bean
    @Order(4)
    public SecurityFilterChain problemReadSecurityFilterChain(
            HttpSecurity http) throws Exception {

        OrRequestMatcher publicProblemReads =
                new OrRequestMatcher(
                        new AntPathRequestMatcher(
                                "/api/problems",
                                HttpMethod.GET.name()),
                        new AntPathRequestMatcher(
                                "/api/problems/*",
                                HttpMethod.GET.name()));

        http
                .securityMatcher(publicProblemReads)
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll());

        return http.build();
    }
}
