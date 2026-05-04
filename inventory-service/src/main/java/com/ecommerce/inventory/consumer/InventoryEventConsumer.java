package com.ecommerce.inventory.consumer;

import com.ecommerce.inventory.dto.OrderEvent;
import com.ecommerce.inventory.exception.InventoryException;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryService inventoryService;
    private final StreamBridge streamBridge;

    @Bean
    public Consumer<OrderEvent> orderCreatedConsumer() {
        return orderEvent -> {
            try {
                if (orderEvent == null) {
                    log.warn("Received null order event in orderCreatedConsumer");
                    return;
                }

                log.info("Saga Step: Checking Inventory for Order: {}", orderEvent);

                // Your logic: check stock based on productId and quantity
                boolean isInStock;
                try {
                    isInStock = inventoryService.checkAndDeductInventory(orderEvent);
                } catch (InventoryException e) {
                    log.error("Inventory check failed: {}", e.getMessage());
                    try {
                        streamBridge.send("inventory-failed-events", orderEvent);
                        log.info("Sent inventory failure event for order: {}", orderEvent.getId());
                    } catch (Exception streamEx) {
                        log.error("Failed to send inventory failure event: {}", streamEx.getMessage(), streamEx);
                    }
                    return;
                }

                if (isInStock) {
                    try {
                        // Pass the ENTIRE payload forward so Payment Service gets the amount/userId
                        streamBridge.send("inventory-reserved-events", orderEvent);
                        log.info("Inventory reserved. Passing to Payment Service.");
                    } catch (Exception e) {
                        log.error("Failed to send inventory reserved event: {}", e.getMessage(), e);
                    }
                } else {
                    try {
                        streamBridge.send("inventory-failed-events", orderEvent);
                        log.warn("Out of stock. Failing Saga.");
                    } catch (Exception e) {
                        log.error("Failed to send inventory failed event: {}", e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected error in orderCreatedConsumer: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }

    // Circuit breaker removed: call inventoryService directly.

    @Bean
    public Consumer<OrderEvent> paymentFailureConsumer() {
        return event -> {
            try {
                if (event == null) {
                    log.warn("Received null event in paymentFailureConsumer");
                    return;
                }

                log.warn("Saga Step: Payment Failed! Restocking inventory for event: {}", event);
                try {
                    // Your logic: find the product and ADD the quantity back to the database
                    inventoryService.restoreInventory(event);
                } catch (InventoryException e) {
                    log.error("Failed to restore inventory: {}", e.getMessage());
                    // Continue even if restoration fails to prevent event listener from crashing
                }
            } catch (Exception e) {
                log.error("Unexpected error in paymentFailureConsumer: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }
}