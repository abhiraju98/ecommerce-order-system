package com.ecommerce.order.listener;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            log.info("Saga Step: Received Payment Success Event via Cloud Stream: {}", event);
            updateOrderStatus(event, OrderStatus.CONFIRMED);
        };
    }

    // Matches 'paymentFailureConsumer-in-0' in YAML
    @Bean
    public Consumer<OrderEvent> paymentFailureConsumer() {
        return event -> {
            log.warn("Saga Step: Received Payment Failure Event! Rolling back order: {}", event);
            updateOrderStatus(event, OrderStatus.PAYMENT_FAILED);
        };
    }

    @Bean
    public Consumer<OrderEvent> inventoryFailureConsumer() {
        return event -> {
            log.warn("Saga Step: Inventory Failed (Out of Stock)! Rolling back order: {}", event);
            updateOrderStatus(event, OrderStatus.OUT_OF_STOCK);
        };
    }

    private void updateOrderStatus(OrderEvent orderEvent, OrderStatus newStatus) {
        try {
            //Long orderId = Long.valueOf(orderIdStr);
            orderRepository.findById(orderEvent.getId()).ifPresentOrElse(order -> {
                order.setStatus(newStatus);
                orderRepository.save(order);
                log.info("Saga Complete: Order {} status successfully updated to {}", orderEvent.getId(), newStatus);
            }, () -> log.error("Saga Error: Order {} not found in database!", orderEvent.getId()));
        } catch (NumberFormatException e) {
            log.error("Saga Error: Could not parse orderId {} to Long", orderEvent.getId());
        }
    }
}