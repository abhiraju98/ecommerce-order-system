package com.ecommerce.order.service;

import com.ecommerce.order.model.OutboxMessage;
import com.ecommerce.order.repository.OutboxRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxRepository outboxRepository;
    private final StreamBridge streamBridge; // Spring's tool to send to Kafka

    // Runs every 5 seconds (5000 milliseconds)
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayOutboxMessages() {
        try {
            // 1. Fetch all pending messages
            List<OutboxMessage> pendingMessages;
            try {
                pendingMessages = outboxRepository.findAll();
            } catch (DataAccessException e) {
                log.error("Failed to fetch messages from outbox database: {}", e.getMessage(), e);
                return; // Early exit on database error
            }

            if (pendingMessages.isEmpty()) {
                log.debug("No pending messages in outbox");
                return; // Nothing to do
            }

            log.info("Found {} messages in outbox. Relaying to Kafka...", pendingMessages.size());

            for (OutboxMessage message : pendingMessages) {
                try {
                    if (message == null || message.getId() == null) {
                        log.warn("Skipping null outbox message");
                        continue;
                    }

                    // 2. Send to Kafka topic via Spring Cloud Stream with Circuit Breaker protection
                    publishMessageWithCircuitBreaker(message);

                } catch (Exception e) {
                    log.error("Unexpected error processing outbox message: {}", e.getMessage(), e);
                    // Continue with next message to avoid breaking the loop
                }
            }
        } catch (Exception e) {
            log.error("Critical error in relayOutboxMessages: {}", e.getMessage(), e);
            // Don't rethrow to prevent scheduler from stopping
        }
    }

    /**
     * Publishes a message to Kafka with circuit breaker protection.
     * If Kafka is experiencing issues, the circuit breaker will prevent
     * overwhelming the service with failed publishes.
     */
    @CircuitBreaker(name = "kafkaPublishingBreaker", fallbackMethod = "publishMessageFallback")
    private void publishMessageWithCircuitBreaker(OutboxMessage message) {
        try {
            // "orderCreatedProducer-out-0" is the binding name set in application.yml
            streamBridge.send("orderCreatedProducer-out-0", message.getPayload());
            log.info("Successfully published message ID: {} to Kafka", message.getId());

            // 3. Delete the message from the outbox so we don't process it again
            try {
                outboxRepository.delete(message);
                log.debug("Deleted outbox message ID: {}", message.getId());
            } catch (DataAccessException e) {
                log.error("Failed to delete outbox message ID {}: {}", message.getId(), e.getMessage(), e);
                // Continue with next message, this one will be retried
            }
        } catch (Exception e) {
            log.error("Failed to publish message ID {} to Kafka: {}", message.getId(), e.getMessage(), e);
            // The message stays in the database and will be retried on the next poll
            throw e; // Re-throw for circuit breaker to handle
        }
    }

    /**
     * Fallback method when circuit breaker is OPEN or service is unavailable.
     * This keeps the message in the database for retry when Kafka recovers.
     */
    private void publishMessageFallback(OutboxMessage message, Exception e) {
        log.warn("Circuit breaker for Kafka publishing is OPEN or service unavailable. " +
                "Message ID {} will be retried later. Reason: {}", message.getId(), e.getMessage());
        // Don't delete the message - it will be retried when circuit breaker closes
        // Also don't throw exception to prevent scheduler from crashing
    }
}