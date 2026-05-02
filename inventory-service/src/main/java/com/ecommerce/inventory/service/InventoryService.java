package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryEvent;
import com.ecommerce.inventory.dto.OrderEvent;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StreamBridge streamBridge; // Add this!

    @Transactional
    public void processOrder(OrderEvent orderEvent) {
        log.info("Processing inventory for Order ID: {}", orderEvent.getId());

        Inventory inventory = inventoryRepository.findByProductId(orderEvent.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        InventoryEvent replyEvent = InventoryEvent.builder()
                .orderId(orderEvent.getId())
                .build();

        if (inventory.getAvailableQuantity() >= 1) {
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
            inventoryRepository.save(inventory);
            log.info("✅ Stock reserved! Remaining: {}", inventory.getAvailableQuantity());

            replyEvent.setStatus("SUCCESS");
        } else {
            log.error("❌ Out of stock for Product {}", inventory.getProductId());
            replyEvent.setStatus("FAILED");
        }

        // Send the reply back to Kafka!
        streamBridge.send("inventoryEventsProducer-out-0", replyEvent);
    }
}