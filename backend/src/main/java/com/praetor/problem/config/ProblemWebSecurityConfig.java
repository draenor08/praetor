package com.praetor.problem.config;

import com.praetor.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
public class ProblemWebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public ProblemWebSecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Public problem reads. The chain stays permitAll, but the JWT filter runs so a token, when one
     * is sent, resolves to a principal — the contest embargo needs to tell an anonymous reader from
     * a registered participant. Without the filter every caller would look anonymous here.
     */
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
                        auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
