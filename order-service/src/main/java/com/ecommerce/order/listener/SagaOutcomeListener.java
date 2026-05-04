package com.ecommerce.order.listener;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import java.util.Map;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SagaOutcomeListener {

    private final OrderRepository orderRepository;

    // Matches 'paymentSuccessConsumer-in-0' in YAML
    @Bean
    public Consumer<OrderEvent> paymentSuccessConsumer() {
        return event -> {
            try {
                if (event == null) {
                    log.warn("Received null event for paymentSuccessConsumer");
                    return;
                }
                log.info("Saga Step: Received Payment Success Event via Cloud Stream: {}", event);
                updateOrderStatus(event, OrderStatus.CONFIRMED);
            } catch (Exception e) {
                log.error("Error processing payment success event: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }

    // Matches 'paymentFailureConsumer-in-0' in YAML
    @Bean
    public Consumer<OrderEvent> paymentFailureConsumer() {
        return event -> {
            try {
                if (event == null) {
                    log.warn("Received null event for paymentFailureConsumer");
                    return;
                }
                log.warn("Saga Step: Received Payment Failure Event! Rolling back order: {}", event);
                updateOrderStatus(event, OrderStatus.PAYMENT_FAILED);
            } catch (Exception e) {
                log.error("Error processing payment failure event: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }

    @Bean
    public Consumer<OrderEvent> inventoryFailureConsumer() {
        return event -> {
            try {
                if (event == null) {
                    log.warn("Received null event for inventoryFailureConsumer");
                    return;
                }
                log.warn("Saga Step: Inventory Failed (Out of Stock)! Rolling back order: {}", event);
                updateOrderStatus(event, OrderStatus.OUT_OF_STOCK);
            } catch (Exception e) {
                log.error("Error processing inventory failure event: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }

    private void updateOrderStatus(OrderEvent orderEvent, OrderStatus newStatus) {
        try {
            if (orderEvent == null || orderEvent.getId() == null) {
                log.error("Invalid order event: null or missing ID");
                return;
            }

            try {
                orderRepository.findById(orderEvent.getId()).ifPresentOrElse(order -> {
                    try {
                        order.setStatus(newStatus);
                        orderRepository.save(order);
                        log.info("Saga Complete: Order {} status successfully updated to {}", 
                                orderEvent.getId(), newStatus);
                    } catch (DataAccessException e) {
                        log.error("Saga Error: Failed to save updated order status for order {}: {}", 
                                orderEvent.getId(), e.getMessage(), e);
                    }
                }, () -> log.error("Saga Error: Order {} not found in database!", orderEvent.getId()));
            } catch (DataAccessException e) {
                log.error("Saga Error: Database access error while fetching order {}: {}", 
                        orderEvent.getId(), e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Saga Error: Unexpected error updating order status: {}", e.getMessage(), e);
        }
    }
}