package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderException;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest orderRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            if (orderRequest == null) {
                throw new OrderException("Order request cannot be null");
            }
            log.info("Processing order request for customer: {}", orderRequest.getCustomerId());
            OrderResponse response = orderService.placeOrder(orderRequest);
            log.info("Order created successfully with ID: {}", response.getId());
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (OrderException e) {
            log.error("Order validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating order: {}", e.getMessage(), e);
            throw new OrderException("Failed to create order: " + e.getMessage(), e);
        }
    }
}