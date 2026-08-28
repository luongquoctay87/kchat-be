package com.kchat.security;

import com.kchat.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TYP = "typ";
    private static final String CLAIM_USERNAME = "username";
    private static final String TYP_ACCESS = "access";

    private final JwtProperties properties;
    private final SecretKey accessKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getAccessSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("kchat.jwt.access-secret must be at least 32 bytes");
        }
        this.accessKey = Keys.hmacShaKeyFor(secret);
    }

    public String createAccessToken(UUID userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTtlMinutes() * 60);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TYP, TYP_ACCESS)
                .claim(CLAIM_USERNAME, username)
                .signWith(accessKey)
                .compact();
    }

    public UserPrincipal parsePrincipal(String accessToken) {
        Claims claims = parseClaims(accessToken);
        Object typ = claims.get(CLAIM_TYP);
        if (typ != null && !TYP_ACCESS.equals(typ.toString())) {
            throw new JwtException("Not an access token");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        if (username == null || username.isBlank()) {
            username = userId.toString();
        }
        return new UserPrincipal(userId, username);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
