package com.ecommerce.auth.util;

import com.ecommerce.auth.exception.AuthException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {
        try {
            if (secret == null || secret.isEmpty()) {
                throw new AuthException("JWT secret is not configured");
            }
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Failed to create signing key: {}", e.getMessage(), e);
            throw new AuthException("Failed to create JWT signing key: " + e.getMessage(), e);
        }
    }

    public String generateToken(String userId, String role) {
        try {
            if (userId == null || userId.isEmpty()) {
                throw new AuthException("User ID cannot be null or empty");
            }

            if (role == null || role.isEmpty()) {
                throw new AuthException("Role cannot be null or empty");
            }

            log.info("Generating JWT token for user: {}, role: {}", userId, role);

            long expirationTimeInMillis = 1000 * 60 * 60 * 24; // 24 Hours

            String token = Jwts.builder()
                    .subject(userId)
                    .claim("role", role)
                    .issuedAt(new Date(System.currentTimeMillis()))
                    .expiration(new Date(System.currentTimeMillis() + expirationTimeInMillis))
                    .signWith(getSigningKey())
                    .compact();

            log.info("JWT token generated successfully for user: {}", userId);
            return token;

        } catch (AuthException e) {
            log.error("Auth exception while generating token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating JWT token: {}", e.getMessage(), e);
            throw new AuthException("Failed to generate JWT token: " + e.getMessage(), e);
        }
    }
}