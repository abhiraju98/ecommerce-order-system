package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class RateLimiterConfig {
    
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            try {
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                
                if (userId != null && !userId.isEmpty()) {
                    log.debug("Using user ID for rate limiting: {}", userId);
                    return Mono.just(userId);
                }

                // Fallback to IP address if no user ID is present
                try {
                    String ipAddress = exchange.getRequest().getRemoteAddress() != null 
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown";
                    log.debug("Using IP address for rate limiting: {}", ipAddress);
                    return Mono.just(ipAddress);
                } catch (Exception e) {
                    log.warn("Failed to extract IP address, using default key: {}", e.getMessage());
                    return Mono.just("default");
                }
            } catch (Exception e) {
                log.error("Unexpected error in userKeyResolver: {}", e.getMessage(), e);
                return Mono.just("default");
            }
        };
    }
}