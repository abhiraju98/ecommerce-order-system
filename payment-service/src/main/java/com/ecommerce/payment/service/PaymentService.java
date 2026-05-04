package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.OrderEvent;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.exception.PaymentException;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StreamBridge streamBridge;

    @Bean
    public Consumer<OrderEvent> inventoryReservedConsumer() {
        return event -> {
            try {
                if (event == null) {
                    log.warn("Received null event in inventoryReservedConsumer");
                    return;
                }
                log.info("Saga Step: Inventory confirmed. Processing payment for: {}", event);
                processAndSavePayment(event);
            } catch (Exception e) {
                log.error("Error processing inventory reserved event: {}", e.getMessage(), e);
                // Don't rethrow to prevent event listener from failing
            }
        };
    }

    /**
     * Processes and saves payment.
     */
    @Transactional
    public void processAndSavePayment(OrderEvent orderEvent) {
        try {
            if (orderEvent == null) {
                throw new PaymentException("Order event cannot be null");
            }

            String productId = orderEvent.getProductId();
            String customerId = orderEvent.getCustomerId();
            BigDecimal amount = orderEvent.getAmount();

            // Validate required fields
            if (productId == null || customerId == null || amount == null) {
                throw new PaymentException("Missing required order fields: productId, customerId, or amount");
            }

            log.info("Processing payment for order: {}, customer: {}, amount: {}", 
                    orderEvent.getId(), customerId, amount);

            boolean paymentSuccessful;
            try {
                paymentSuccessful = simulateStripePayment(amount);
            } catch (Exception e) {
                log.error("Error during payment simulation: {}", e.getMessage(), e);
                throw new PaymentException("Payment simulation failed: " + e.getMessage(), e);
            }

            Payment payment = Payment.builder()
                    .productId(productId)
                    .customerId(customerId)
                    .amount(amount)
                    .status(paymentSuccessful ? "SUCCESS" : "FAILED")
                    .timestamp(LocalDateTime.now())
                    .build();

            try {
                paymentRepository.save(payment);
                log.info("Payment record saved successfully");
            } catch (DataAccessException e) {
                log.error("Failed to save payment record: {}", e.getMessage(), e);
                throw new PaymentException("Failed to save payment record to database: " + e.getMessage(), e);
            }

            try {
                publishPaymentResult(orderEvent, paymentSuccessful);
            } catch (Exception e) {
                log.error("Failed to publish payment result: {}", e.getMessage(), e);
                throw new PaymentException("Failed to publish payment result: " + e.getMessage(), e);
            }

        } catch (PaymentException e) {
            log.error("Payment exception occurred: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in processAndSavePayment: {}", e.getMessage(), e);
            throw new PaymentException("Unexpected error processing payment: " + e.getMessage(), e);
        }
    }

    // Circuit breaker fallback removed: operate without Resilience4j annotations here.

    private boolean simulateStripePayment(BigDecimal amount) {
        try {
            if (amount == null) {
                log.warn("Amount is null, rejecting payment");
                return false;
            }
            return amount.compareTo(new BigDecimal("1000.00")) < 0;
        } catch (Exception e) {
            log.error("Error during payment simulation: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Publishes payment result with circuit breaker protection.
     */
    private void publishPaymentResult(OrderEvent order, boolean isSuccess) {
        try {
            if (order == null) {
                log.error("Order is null, cannot publish payment result");
                return;
            }

            String destinationTopic = isSuccess ? "payment-success-events" : "payment-failed-events";

            order.setStatus(String.valueOf(isSuccess ? PaymentStatus.PAYMENT_COMPLETE : PaymentStatus.PAYMENT_FAILED));

            try {
                streamBridge.send(destinationTopic, order);
                log.info("Published Payment Result to topic {}: {}", destinationTopic, order);
            } catch (Exception e) {
                log.error("Failed to send payment result to Kafka topic {}: {}", destinationTopic, e.getMessage(), e);
                throw new PaymentException("Failed to publish payment result to stream: " + e.getMessage(), e);
            }
        } catch (PaymentException e) {
            log.error("Payment publication error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in publishPaymentResult: {}", e.getMessage(), e);
            throw new PaymentException("Unexpected error publishing payment result: " + e.getMessage(), e);
        }
    }
}