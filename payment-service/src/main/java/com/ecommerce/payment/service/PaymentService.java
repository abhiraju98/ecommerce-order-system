package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.OrderEvent;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
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
            log.info("Saga Step: Inventory confirmed. Processing payment for: {}", event);
            processAndSavePayment(event);
        };
    }

    // 3. The transaction is safely managed here
    @Transactional
    public void processAndSavePayment(OrderEvent orderEvent) {
        String productId = orderEvent.getProductId();
        String customerId = orderEvent.getCustomerId();
        BigDecimal amount = orderEvent.getAmount();

        boolean paymentSuccessful = simulateStripePayment(amount);

        Payment payment = Payment.builder()
                .productId(productId)
                .customerId(customerId)
                .amount(amount)
                .status(paymentSuccessful ? "SUCCESS" : "FAILED")
                .timestamp(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        publishPaymentResult(orderEvent, paymentSuccessful);
    }

    private boolean simulateStripePayment(BigDecimal amount) {
        return amount.compareTo(new BigDecimal("1000.00")) < 0;
    }

    private void publishPaymentResult(OrderEvent order, boolean isSuccess) {
        String destinationTopic = isSuccess ? "payment-success-events" : "payment-failed-events";

        order.setStatus(String.valueOf(isSuccess ? PaymentStatus.PAYMENT_COMPLETE : PaymentStatus.PAYMENT_FAILED));

        streamBridge.send(destinationTopic, order);
        log.info("Published Payment Result to topic {}: {}", destinationTopic, order);
    }
}