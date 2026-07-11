package com.praetor.ws;

import com.praetor.common.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP session at the CONNECT frame (the {@code /ws} HTTP handshake itself is
 * permitted by {@link WsSecurityConfig}). Reads the JWT from the {@code Authorization} native
 * header, validates it, and sets the {@link java.security.Principal} on the session — that Principal
 * (the username) is what lets {@code convertAndSendToUser} route to {@code /user/queue/...}.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public StompAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("missing bearer token on STOMP CONNECT");
        }
        String token = header.substring(7);
        String username = jwtService.extractUsername(token);
        if (username == null) {
            throw new MessagingException("invalid token");
        }
        UserDetails user = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, user)) {
            throw new MessagingException("invalid token");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return message;
    }
}
