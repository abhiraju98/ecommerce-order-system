package com.ecommerce.order.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderRequest {
    private String customerId;
    private String productId;
    private BigDecimal amount;
}