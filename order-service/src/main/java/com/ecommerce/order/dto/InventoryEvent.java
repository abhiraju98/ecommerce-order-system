package com.ecommerce.order.dto;

import lombok.Data;

@Data
public class InventoryEvent {
    private Long orderId;
    private String status;
}