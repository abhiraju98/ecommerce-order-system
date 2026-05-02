package com.ecommerce.order.consumer;

import com.ecommerce.order.dto.InventoryEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReplyConsumer {

    private final OrderRepository orderRepository;

    @Bean
    public Consumer<InventoryEvent> inventoryReplyEvent() {
        return event -> {
            log.info("🔔 ORDER SERVICE RECEIVED INVENTORY REPLY for Order ID: {} with Status: {}",
                    event.getOrderId(), event.getStatus());

            // 1. Find the order in the database
            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            // 2. Update the status based on the inventory result
            if ("SUCCESS".equals(event.getStatus())) {
                order.setStatus(OrderStatus.CONFIRMED);
                log.info("✅ Order {} is now CONFIRMED", order.getId());
            } else {
                order.setStatus(OrderStatus.CANCELLED);
                log.info("❌ Order {} is now CANCELLED due to out-of-stock", order.getId());
            }

            // 3. Save the updated order
            orderRepository.save(order);
            log.info("--------------------------------------------------");
        };
    }
}