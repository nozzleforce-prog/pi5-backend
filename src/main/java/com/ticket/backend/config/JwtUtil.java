package com.ticket.backend.config;

import com.ticket.backend.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final Key key;
    private final long expirationTimeMs;

    public JwtUtil(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationTimeMs) {
        this.expirationTimeMs = expirationTimeMs;
        this.key = resolveKey(secret);
    }

    private static Key resolveKey(String secret) {
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "app.jwt.secret / APP_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
            }
            return Keys.hmacShaKeyFor(bytes);
        }
        log.warn("APP_JWT_SECRET is empty — using ephemeral JWT key (tokens reset on restart)");
        return Keys.secretKeyFor(SignatureAlgorithm.HS256);
    }

    public String generateToken(String username, UserRole role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTimeMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public UserRole extractRole(String token) {
        Claims claims = parseClaims(token);
        String role = claims.get("role", String.class);
        if (role == null) {
            return UserRole.OPERATOR;
        }
        return UserRole.valueOf(role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
                .getBody();
    }
}
