package com.ecommerce.inventory.config;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) throws Exception {
        try {
            // If the database is empty, add our test product
            long inventoryCount;
            try {
                inventoryCount = inventoryRepository.count();
            } catch (DataAccessException e) {
                log.error("Failed to count inventory records: {}", e.getMessage(), e);
                return;
            }

            if (inventoryCount == 0) {
                try {
                    inventoryRepository.save(
                            Inventory.builder()
                                    .productId("PROD-12345")
                                    .availableQuantity(10) // We have 10 in stock!
                                    .build()
                    );
                    log.info("✅ Seeded database with dummy inventory.");
                    System.out.println("✅ Seeded database with dummy inventory.");
                } catch (DataAccessException e) {
                    log.error("Failed to save initial inventory data: {}", e.getMessage(), e);
                }
            } else {
                log.info("Inventory database already populated with {} records", inventoryCount);
            }
        } catch (Exception e) {
            log.error("Unexpected error during DataLoader initialization: {}", e.getMessage(), e);
        }
    }
}