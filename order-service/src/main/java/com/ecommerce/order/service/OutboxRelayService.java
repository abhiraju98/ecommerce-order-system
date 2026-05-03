package com.ecommerce.order.service;

import com.ecommerce.order.model.OutboxMessage;
import com.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
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
        // 1. Fetch all pending messages
        List<OutboxMessage> pendingMessages = outboxRepository.findAll();

        if (pendingMessages.isEmpty()) {
            return; // Nothing to do
        }

        log.info("Found {} messages in outbox. Relaying to Kafka...", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                // 2. Send to Kafka topic via Spring Cloud Stream
                // "orderCreatedProducer-out-0" is the binding name we will set in application.yml
                streamBridge.send("orderCreatedProducer-out-0", message.getPayload());

                // 3. Delete the message from the outbox so we don't process it again
                outboxRepository.delete(message);

                log.info("Successfully published message ID: {}", message.getId());

            } catch (Exception e) {
                log.error("Failed to publish message ID: {}", message.getId(), e);
                // The message stays in the database and will be retried on the next poll
            }
        }
    }
}