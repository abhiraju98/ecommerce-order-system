package com.ecommerce.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class FallbackController {

    @RequestMapping("/fallback/order")
    public ResponseEntity<String> orderFallback() {
        try {
            log.warn("Order Service fallback triggered");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Order Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error in order fallback handler: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your request");
        }
    }

    @RequestMapping("/fallback/inventory")
    public ResponseEntity<String> inventoryFallback() {
        try {
            log.warn("Inventory Service fallback triggered");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Inventory Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error in inventory fallback handler: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your request");
        }
    }

    @RequestMapping("/fallback/payment")
    public ResponseEntity<String> paymentFallback() {
        try {
            log.warn("Payment Service fallback triggered");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Payment Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error in payment fallback handler: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your request");
        }
    }

    @RequestMapping("/fallback/auth")
    public ResponseEntity<String> authFallback() {
        try {
            log.warn("Auth Service fallback triggered");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Authentication Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error in auth fallback handler: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your request");
        }
    }
}