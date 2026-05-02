package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private OrderStatus status;
}