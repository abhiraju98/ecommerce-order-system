package com.ecommerce.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class DiagnosticsController {

    // Note: We make this an "internal" path so it isn't directly exposed to the outside world
    @GetMapping("/internal/check-auth")
    public Mono<ResponseEntity<String>> checkAuth(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId == null) {
            return Mono.just(ResponseEntity.badRequest().body("Token validated, but User ID is missing!"));
        }

        return Mono.just(ResponseEntity.ok(
                "✅ JWT successfully validated by Gateway! Extracted User ID: " + userId
        ));
    }
}