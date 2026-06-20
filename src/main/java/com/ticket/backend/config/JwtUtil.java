package com.ticket.backend.config;

import com.ticket.backend.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final long EXPIRATION_TIME = 86400000; // 1 day

    public String generateToken(String username, UserRole role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
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


