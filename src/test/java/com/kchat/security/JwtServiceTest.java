package com.kchat.security;

import com.kchat.config.JwtProperties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setAccessSecret("unit-test-access-secret-at-least-32b!");
        props.setAccessTtlMinutes(5);
        jwtService = new JwtService(props);
    }

    @Test
    void createAndParseRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createAccessToken(userId, "nguyenva");
        UserPrincipal principal = jwtService.parsePrincipal(token);
        assertEquals(userId, principal.getId());
        assertEquals("nguyenva", principal.getUsername());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createAccessToken(UUID.randomUUID(), "u");
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertThrows(Exception.class, () -> jwtService.parsePrincipal(tampered));
    }
}
