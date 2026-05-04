package com.ecommerce.order.service;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.OutboxMessage;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // Number of minutes after which a PENDING order should be cancelled
    @Value("${order.expiry.minutes:10}")
    private long expiryMinutes;

    // How often to check (milliseconds)
    @Scheduled(fixedDelayString = "${order.expiry.check-interval-ms:60000}")
    @Transactional
    public void expirePendingOrders() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expiryMinutes);
            List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

            if (expiredOrders.isEmpty()) {
                log.debug("No pending orders older than {} minutes", expiryMinutes);
                return;
            }

            log.info("Found {} pending orders older than {} minutes. Cancelling...", expiredOrders.size(), expiryMinutes);

            for (Order order : expiredOrders) {
                try {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);

                    // Publish cancellation to outbox so other services can react if needed
                    try {
                        String payload = objectMapper.writeValueAsString(order);
                        OutboxMessage outboxMessage = OutboxMessage.builder()
                                .aggregateType("Order")
                                .aggregateId(order.getId().toString())
                                .eventType("OrderCancelled")
                                .payload(payload)
                                .build();
                        outboxRepository.save(outboxMessage);
                        log.info("Order {} cancelled and outbox message created.", order.getId());
                    } catch (Exception e) {
                        log.error("Failed to create outbox message for cancelled order {}: {}", order.getId(), e.getMessage(), e);
                    }

                } catch (Exception e) {
                    log.error("Failed to cancel order {}: {}", order.getId(), e.getMessage(), e);
                    // Continue processing other orders
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error while expiring pending orders: {}", e.getMessage(), e);
        }
    }
}

