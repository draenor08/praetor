package com.praetor.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A dedicated, higher-precedence {@link SecurityFilterChain} for {@code /ws/**} so the SockJS
 * handshake isn't blocked by the identity module's {@code anyRequest().authenticated()} default
 * chain. Kept as a SEPARATE bean on purpose — it does not edit the teammate-owned SecurityConfig.
 * Actual auth happens at the STOMP CONNECT frame ({@link StompAuthChannelInterceptor}); the HTTP
 * handshake only needs to be reachable.
 */
@Configuration
public class WsSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain wsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/ws/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
