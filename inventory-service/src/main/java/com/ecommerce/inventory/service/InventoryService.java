package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryEvent;
import com.ecommerce.inventory.dto.OrderEvent;
import com.ecommerce.inventory.exception.InventoryException;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryStatus;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StreamBridge streamBridge; // Add this!

    @Transactional
    public boolean checkAndDeductInventory(OrderEvent orderEvent) {
        try {
            if (orderEvent == null) {
                log.error("Order event is null");
                throw new InventoryException("Order event cannot be null");
            }

            if (orderEvent.getProductId() == null) {
                log.error("Product ID is null in order event");
                throw new InventoryException("Product ID cannot be null");
            }

            log.info("Processing inventory for Order ID: {}", orderEvent.getId());

            Inventory inventory;
            try {
                Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(orderEvent.getProductId());
                inventory = inventoryOpt.orElseThrow(() -> new InventoryException("Product not found: " + orderEvent.getProductId()));
            } catch (DataAccessException e) {
                log.error("Database error while fetching inventory: {}", e.getMessage(), e);
                throw new InventoryException("Failed to fetch inventory from database: " + e.getMessage(), e);
            }

            InventoryEvent replyEvent = InventoryEvent.builder()
                    .orderId(orderEvent.getId())
                    .build();

            boolean isInStock = false;

            try {
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
            } catch (DataAccessException e) {
                log.error("Failed to update inventory: {}", e.getMessage(), e);
                throw new InventoryException("Failed to update inventory in database: " + e.getMessage(), e);
            }

            return isInStock;

        } catch (InventoryException e) {
            log.error("Inventory exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in checkAndDeductInventory: {}", e.getMessage(), e);
            throw new InventoryException("Unexpected error checking inventory: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void restoreInventory(OrderEvent event) {
        try {
            if (event == null) {
                log.error("Order event is null");
                throw new InventoryException("Order event cannot be null");
            }

            if (event.getProductId() == null) {
                log.error("Product ID is null in order event");
                throw new InventoryException("Product ID cannot be null");
            }

            log.info("Restoring inventory for product: {}", event.getProductId());

            try {
                Inventory inventory = inventoryRepository.findByProductId(event.getProductId())
                        .orElseThrow(() -> new InventoryException("Product not found for restoration: " + event.getProductId()));

                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + 1);
                inventoryRepository.save(inventory);
                log.info("🔄 Inventory restored for Product {}. New quantity: {}", inventory.getProductId(), inventory.getAvailableQuantity());

            } catch (DataAccessException e) {
                log.error("Database error while restoring inventory: {}", e.getMessage(), e);
                throw new InventoryException("Failed to restore inventory in database: " + e.getMessage(), e);
            }
        } catch (InventoryException e) {
            log.error("Inventory exception during restoration: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in restoreInventory: {}", e.getMessage(), e);
            throw new InventoryException("Unexpected error restoring inventory: " + e.getMessage(), e);
        }
    }
}