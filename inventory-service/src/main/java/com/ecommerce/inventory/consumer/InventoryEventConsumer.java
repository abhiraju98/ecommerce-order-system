package com.ecommerce.inventory.consumer;

import com.ecommerce.inventory.dto.OrderEvent;
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
            log.info("Saga Step: Checking Inventory for Order: {}", orderEvent);

            // Your logic: check stock based on productId and quantity
            boolean isInStock = inventoryService.checkAndDeductInventory(orderEvent);

            if (isInStock) {
                // Pass the ENTIRE payload forward so Payment Service gets the amount/userId
                streamBridge.send("inventory-reserved-events", orderEvent);
                log.info("Inventory reserved. Passing to Payment Service.");
            } else {
                streamBridge.send("inventory-failed-events", orderEvent);
                log.warn("Out of stock. Failing Saga.");
            }
        };
    }

    @Bean
    public Consumer<OrderEvent> paymentFailureConsumer() {
        return event -> {
            log.warn("Saga Step: Payment Failed! Restocking inventory for event: {}", event);
            // Your logic: find the product and ADD the quantity back to the database
            inventoryService.restoreInventory(event);
        };
    }
}