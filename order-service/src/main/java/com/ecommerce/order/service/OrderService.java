package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.OutboxMessage;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {

        // 1. Build and save the Order
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);

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

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize order event for outbox", e);
        }

        // 3. Map back to Response DTO
        return OrderResponse.builder()
                .id(savedOrder.getId())
                .customerId(savedOrder.getCustomerId())
                .productId(savedOrder.getProductId())
                .amount(savedOrder.getAmount())
                .status(savedOrder.getStatus())
                .build();
    }
}