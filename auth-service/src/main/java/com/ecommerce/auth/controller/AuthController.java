package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AuthRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.exception.AuthException;
import com.ecommerce.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            if (request == null) {
                throw new AuthException("Login request cannot be null");
            }

            if (request.getUsername() == null || request.getPassword() == null) {
                log.warn("Login attempt with missing credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, "Invalid username or password"));
            }

            log.info("Login attempt for user: {}", request.getUsername());

            // Hardcoded validation for portfolio MVP
            if ("admin".equals(request.getUsername()) && "password123".equals(request.getPassword())) {
                try {
                    // Generate token with a mock User ID (e.g., USER-999)
                    String token = jwtUtil.generateToken("USER-999", "ADMIN");
                    log.info("Login successful for user: admin");
                    return ResponseEntity.ok(new AuthResponse(token, "Login successful"));
                } catch (Exception e) {
                    log.error("Failed to generate JWT token: {}", e.getMessage(), e);
                    throw new AuthException("Failed to generate authentication token: " + e.getMessage(), e);
                }
            }

            log.warn("Login failed for user: {} - invalid credentials", request.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Invalid username or password"));

        } catch (AuthException e) {
            log.error("Authentication exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login: {}", e.getMessage(), e);
            throw new AuthException("Unexpected error during login: " + e.getMessage(), e);
        }
    }
}