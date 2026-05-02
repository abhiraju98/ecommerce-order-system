package com.ecommerce.inventory.consumer;

import com.ecommerce.inventory.dto.OrderEvent;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @Bean
    public Consumer<OrderEvent> orderCreatedEvent() {
        return event -> {
            log.info("🔔 INVENTORY SERVICE RECEIVED NEW ORDER EVENT!");

            try {
                // Call the database logic
                inventoryService.processOrder(event);
            } catch (Exception e) {
                log.error("Error processing inventory for order: {}", e.getMessage());
            }

            log.info("--------------------------------------------------");
        };
    }
}