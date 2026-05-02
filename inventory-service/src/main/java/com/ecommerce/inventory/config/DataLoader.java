package com.ecommerce.inventory.config;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) throws Exception {
        // If the database is empty, add our test product
        if (inventoryRepository.count() == 0) {
            inventoryRepository.save(
                    Inventory.builder()
                            .productId("PROD-12345")
                            .availableQuantity(10) // We have 10 in stock!
                            .build()
            );
            System.out.println("✅ Seeded database with dummy inventory.");
        }
    }
}