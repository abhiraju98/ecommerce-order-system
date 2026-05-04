package com.ecommerce.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Circuit Breaker Configuration for Order Service
 * Protects against cascading failures when calling downstream services via Kafka
 */
@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    /**
     * Circuit breaker for Kafka event publishing
     * Prevents outbox relay service from overwhelming Kafka when it's experiencing issues
     */
    @Bean
    public CircuitBreaker kafkaPublishingCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)                    // Trip if 50% of calls fail
                .slowCallRateThreshold(50.0f)                   // Trip if 50% of calls are slow
                .slowCallDurationThreshold(Duration.ofSeconds(3)) // Calls > 3s are considered slow
                .waitDurationInOpenState(Duration.ofSeconds(10))  // Wait 10s before half-open
                .permittedNumberOfCallsInHalfOpenState(5)        // Try 5 calls in half-open state
                .slidingWindowSize(20)                           // Use last 20 calls
                .minimumNumberOfCalls(5)                         // Need 5 calls before evaluating
                .recordExceptions(Exception.class)               // Record all exceptions
                .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafkaPublishingBreaker", config);
        
        // Add event consumer for logging
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Kafka Publishing Circuit Breaker State Change: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> log.warn("Kafka publish call not permitted by circuit breaker"))
                .onError(event -> {
                    if (event.getThrowable() != null) {
                        log.error("Kafka publish failed: {}", event.getThrowable().getMessage());
                    } else {
                        log.error("Kafka publish failed with unknown error: {}", event.toString());
                    }
                });

        return circuitBreaker;
    }

    /**
     * Circuit breaker for inventory checks
     * If inventory service is down, order processing should still fail gracefully
     */
    @Bean
    public CircuitBreaker inventoryServiceCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60.0f)
                .slowCallRateThreshold(40.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .recordExceptions(Exception.class)
                .build();

        return circuitBreakerRegistry.circuitBreaker("inventoryServiceBreaker", config);
    }

    /**
     * Circuit breaker for payment service
     * More strict since payment is critical
     */
    @Bean
    public CircuitBreaker paymentServiceCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(30.0f)                    // More strict threshold
                .slowCallRateThreshold(20.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(20)) // Longer wait time
                .permittedNumberOfCallsInHalfOpenState(2)       // Fewer retries
                .slidingWindowSize(15)
                .minimumNumberOfCalls(5)
                .recordExceptions(Exception.class)
                .build();

        return circuitBreakerRegistry.circuitBreaker("paymentServiceBreaker", config);
    }

    /**
     * Registers a consumer to log all circuit breaker events
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> myRegistryEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("Circuit Breaker registered: {}", circuitBreaker.getName());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit Breaker removed: {}", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit Breaker replaced: {} -> {}",
                        entryReplacedEvent.getOldEntry().getName(),
                        entryReplacedEvent.getNewEntry().getName());
            }
        };
    }
}

