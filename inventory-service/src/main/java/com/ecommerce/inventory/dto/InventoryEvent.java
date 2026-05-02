package com.ecommerce.inventory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryEvent {
    private Long orderId;
    private String status; // "SUCCESS" or "FAILED"
}