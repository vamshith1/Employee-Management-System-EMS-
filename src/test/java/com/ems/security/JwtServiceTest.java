package com.ems.security;

import com.ems.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("a-test-secret-that-is-at-least-32-bytes-long!".getBytes());

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));
    private final AppUserPrincipal user = new AppUserPrincipal(1L, "alice", "hash", Role.HR, 5L, true);

    @Test
    void roundTripsUsername() {
        String token = jwtService.generateToken(user);
        assertThat(jwtService.validateAndGetUsername(token)).contains("alice");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(jwtService.validateAndGetUsername(tampered)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String otherSecret = Base64.getEncoder().encodeToString("another-secret-that-is-also-32-bytes-long!!".getBytes());
        String foreign = new JwtService(new JwtProperties(otherSecret, 60)).generateToken(user);
        assertThat(jwtService.validateAndGetUsername(foreign)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = new JwtService(new JwtProperties(SECRET, -1)).generateToken(user);
        assertThat(jwtService.validateAndGetUsername(expired)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwtService.validateAndGetUsername("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> new JwtService(new JwtProperties(shortSecret, 60)))
                .isInstanceOf(IllegalStateException.class);
    }
}
