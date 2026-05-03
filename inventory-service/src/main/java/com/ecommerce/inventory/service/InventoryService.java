package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryEvent;
import com.ecommerce.inventory.dto.OrderEvent;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryStatus;
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
    public boolean checkAndDeductInventory(OrderEvent orderEvent) {
        log.info("Processing inventory for Order ID: {}", orderEvent.getId());

        Inventory inventory = inventoryRepository.findByProductId(orderEvent.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        InventoryEvent replyEvent = InventoryEvent.builder()
                .orderId(orderEvent.getId())
                .build();

        boolean isInStock = false;

        if (inventory.getAvailableQuantity() >= 1) {
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
            inventoryRepository.save(inventory);
            log.info("✅ Stock reserved! Remaining: {}", inventory.getAvailableQuantity());

            replyEvent.setStatus(InventoryStatus.INVENTORY_BLOCKED);
            isInStock = true;
        } else {
            log.error("❌ Out of stock for Product {}", inventory.getProductId());
            replyEvent.setStatus(InventoryStatus.OUT_OF_STOCK);
        }

        return isInStock;
    }

    public void restoreInventory(OrderEvent event) {
        Inventory inventory = inventoryRepository.findByProductId(event.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + 1);
        inventoryRepository.save(inventory);
        log.info("🔄 Inventory restored for Product {}. New quantity: {}", inventory.getProductId(), inventory.getAvailableQuantity());
    }
}