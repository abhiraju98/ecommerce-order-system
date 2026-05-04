package com.ecommerce.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String secretKey;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            try {
                ServerHttpRequest request = exchange.getRequest();

                // 1. Check if Authorization header exists
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    log.warn("Missing Authorization header in request");
                    return onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
                }

                String authHeader = null;
                try {
                    authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                } catch (Exception e) {
                    log.warn("Error retrieving Authorization header: {}", e.getMessage());
                    return onError(exchange, "Invalid Authorization Header format", HttpStatus.UNAUTHORIZED);
                }

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.warn("Invalid Authorization header format");
                    return onError(exchange, "Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
                }

                String token = authHeader.substring(7);

                if (token.isEmpty()) {
                    log.warn("Empty JWT token provided");
                    return onError(exchange, "Empty token provided", HttpStatus.UNAUTHORIZED);
                }

                // 2. Validate JWT (Gateway offloads this from microservices)
                try {
                    // 1. Generate the secure key object
                    if (secretKey == null || secretKey.isEmpty()) {
                        log.error("JWT secret is not configured");
                        return onError(exchange, "Server configuration error", HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

                    // 2. Parse using the strict 0.12.5 standard
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    String extractedUserId = claims.getSubject();

                    if (extractedUserId == null || extractedUserId.isEmpty()) {
                        log.warn("JWT token does not contain a valid subject (userId)");
                        return onError(exchange, "Invalid token: missing user ID", HttpStatus.UNAUTHORIZED);
                    }

                    log.debug("GATEWAY EXTRACTED USER ID: {}", extractedUserId);
                    System.out.println("GATEWAY EXTRACTED USER ID: " + extractedUserId);

                    // 3. Mutate the request
                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("X-User-Id", extractedUserId)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());

                } catch (JwtException e) {
                    log.warn("JWT Token validation failed: {}", e.getMessage());
                    return onError(exchange, "JWT Token Validation Failed: Invalid or expired token", HttpStatus.UNAUTHORIZED);
                } catch (IllegalArgumentException e) {
                    log.warn("JWT claims string is empty: {}", e.getMessage());
                    return onError(exchange, "JWT Token Validation Failed: Empty token", HttpStatus.UNAUTHORIZED);
                } catch (Exception e) {
                    log.error("Unexpected error during JWT validation: {}", e.getMessage(), e);
                    return onError(exchange, "JWT Token Validation Failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
                }

            } catch (Exception e) {
                log.error("Unexpected error in AuthenticationFilter: {}", e.getMessage(), e);
                return onError(exchange, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        try {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(httpStatus);
            log.warn("Sending error response: status={}, message={}", httpStatus, err);
            return response.setComplete();
        } catch (Exception e) {
            log.error("Error sending error response: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    public static class Config {
        // Configuration properties can be added here
    }
}