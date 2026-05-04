package com.ecommerce.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class DiagnosticsController {

    // Note: We make this an "internal" path so it isn't directly exposed to the outside world
    @GetMapping("/internal/check-auth")
    public Mono<ResponseEntity<String>> checkAuth(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            if (userId == null) {
                log.warn("Auth check failed: User ID is missing from header");
                return Mono.just(ResponseEntity.badRequest()
                        .body("Token validated, but User ID is missing!"));
            }

            log.info("Auth check successful for user: {}", userId);
            return Mono.just(ResponseEntity.ok(
                    "✅ JWT successfully validated by Gateway! Extracted User ID: " + userId
            ));
        } catch (Exception e) {
            log.error("Unexpected error in checkAuth: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.internalServerError()
                    .body("An unexpected error occurred during authentication check"));
        }
    }
}