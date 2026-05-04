package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderException;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.OutboxMessage;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        try {
            // Validate input
            if (request == null || request.getCustomerId() == null || request.getProductId() == null) {
                throw new OrderException("Invalid order request: missing required fields");
            }

            log.info("Creating order for customer: {}, product: {}, amount: {}", 
                    request.getCustomerId(), request.getProductId(), request.getAmount());

            // 1. Build and save the Order
            Order order = Order.builder()
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .amount(request.getAmount())
                    .status(OrderStatus.PENDING)
                    .build();

            Order savedOrder;
            try {
                savedOrder = orderRepository.save(order);
                log.info("Order saved successfully with ID: {}", savedOrder.getId());
            } catch (Exception e) {
                log.error("Failed to save order to database: {}", e.getMessage(), e);
                throw new OrderException("Failed to save order to database: " + e.getMessage(), e);
            }

            // 2. Prepare and save the Outbox Event
            try {
                String eventPayload = objectMapper.writeValueAsString(savedOrder);

                OutboxMessage outboxMessage = OutboxMessage.builder()
                        .aggregateType("Order")
                        .aggregateId(savedOrder.getId().toString())
                        .eventType("OrderCreated")
                        .payload(eventPayload)
                        .build();

                outboxRepository.save(outboxMessage);
                log.info("Outbox message saved for order ID: {}", savedOrder.getId());

            } catch (Exception e) {
                log.error("Failed to serialize order event for outbox: {}", e.getMessage(), e);
                throw new OrderException("Failed to serialize order event for outbox: " + e.getMessage(), e);
            }

            // 3. Map back to Response DTO
            OrderResponse response = OrderResponse.builder()
                    .id(savedOrder.getId())
                    .customerId(savedOrder.getCustomerId())
                    .productId(savedOrder.getProductId())
                    .amount(savedOrder.getAmount())
                    .status(savedOrder.getStatus())
                    .build();
            
            log.info("Order response prepared successfully");
            return response;
            
        } catch (OrderException e) {
            log.error("Order exception occurred: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error placing order: {}", e.getMessage(), e);
            throw new OrderException("Unexpected error placing order: " + e.getMessage(), e);
        }
    }
}