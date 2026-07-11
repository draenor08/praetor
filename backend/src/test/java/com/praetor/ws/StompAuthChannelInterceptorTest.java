package com.praetor.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.praetor.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    JwtService jwtService;
    @Mock
    UserDetailsService userDetailsService;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, userDetailsService);
    }

    private Message<byte[]> connectFrame(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // the real inbound channel hands interceptors a mutable message (so auth can set the
        // Principal) — mirror that here, else accessor.setUser() hits "Already immutable"
        accessor.setLeaveMutable(true);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void rejects_missing_authorization_header() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame(null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejects_non_bearer_header() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Basic abc"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void accepts_valid_token() {
        UserDetails ud = User.withUsername("alice").password("x").authorities("ROLE_USER").build();
        when(jwtService.extractUsername("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(ud);
        when(jwtService.isTokenValid("good", ud)).thenReturn(true);

        Message<?> out = interceptor.preSend(connectFrame("Bearer good"), null);
        assertThat(out).isNotNull();
    }

    @Test
    void rejects_invalid_token() {
        UserDetails ud = User.withUsername("alice").password("x").authorities("ROLE_USER").build();
        when(jwtService.extractUsername("bad")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(ud);
        when(jwtService.isTokenValid("bad", ud)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Bearer bad"), null))
                .isInstanceOf(MessagingException.class);
    }
}
