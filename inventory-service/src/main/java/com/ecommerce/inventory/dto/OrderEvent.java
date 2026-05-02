package com.ecommerce.inventory.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderEvent {
    private Long id;
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private String status;
}