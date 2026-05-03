package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.model.InventoryStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryEvent {
    private Long orderId;
    private InventoryStatus status; // "SUCCESS" or "FAILED"
}