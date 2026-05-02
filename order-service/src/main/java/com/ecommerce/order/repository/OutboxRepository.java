package com.ecommerce.order.repository;

import com.ecommerce.order.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
}