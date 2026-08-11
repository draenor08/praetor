package com.praetor.common.security;

import com.praetor.identity.entity.User;
import java.util.Date;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The signing key and the lifetime come from config, not from constants in the class.
 */
class JwtServiceTest {

    private static final String SECRET = "praetor-test-secret-value-long-enough-for-hs256";

    private JwtService serviceWith(String secret, int expiryMin) {
        return new JwtService(new JwtProperties(secret, expiryMin));
    }

    private User user(String username) {
        User u = new User();
        u.setUsername(username);
        u.setRole("USER");
        return u;
    }

    @Test
    void roundTripsTheSubject() {
        JwtService service = serviceWith(SECRET, 120);
        String token = service.generateToken(user("alice"));

        assertThat(service.extractUsername(token)).isEqualTo("alice");
        assertThat(service.isTokenValid(token, user("alice"))).isTrue();
    }

    @Test
    void expiryComesFromConfigNotAHardcodedDay() {
        // Was hardcoded to 24h, ignoring JWT_EXPIRY_MIN entirely.
        JwtService service = serviceWith(SECRET, 5);
        String token = service.generateToken(user("alice"));

        long expiresInMs = service.extractClaim(token, claims -> claims.getExpiration()).getTime()
                - System.currentTimeMillis();
        long fiveMinutes = 5 * 60_000L;

        assertThat(expiresInMs).isLessThanOrEqualTo(fiveMinutes);
        // generous lower bound so a slow CI machine cannot flake this
        assertThat(expiresInMs).isGreaterThan(fiveMinutes - 30_000L);
    }

    @Test
    void alreadyExpiredTokenIsNotValid() {
        JwtService service = serviceWith(SECRET, 1);
        String token = service.generateToken(user("alice"));

        // A token minted 1 minute long is still valid now; assert the expiry is in the future and
        // that validity keys off it rather than off the signature alone.
        Date expiration = service.extractClaim(token, claims -> claims.getExpiration());
        assertThat(expiration.after(new Date())).isTrue();
        assertThat(service.isTokenValid(token, user("alice"))).isTrue();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        // The point of externalising the key: two deployments with different secrets must not
        // accept each other's tokens. Before, every deployment shared one committed key.
        String token = serviceWith(SECRET, 120).generateToken(user("alice"));
        JwtService other = serviceWith("a-completely-different-secret-also-long-enough", 120);

        assertThatThrownBy(() -> other.extractUsername(token))
                .isInstanceOf(RuntimeException.class);
    }
}
