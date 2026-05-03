# 🛒 Enterprise E-Commerce Microservices Platform

A production-ready, event-driven microservices architecture designed to handle high-throughput e-commerce workflows.

This project goes beyond basic CRUD operations to tackle the complex realities of distributed systems, implementing advanced design patterns for data consistency, network resilience, and security at scale.

## 🏗️ System Architecture
```mermaid
graph TD
    Client([Client / Frontend App]) -->|HTTP POST| Gateway(Spring Cloud API Gateway)
    
    subgraph Edge Layer
        Gateway --> |1. Check Rate Limit| Redis[(Redis)]
        Gateway --> |2. Validate JWT| AuthFilter{Auth Filter}
        AuthFilter --> |Mutate Header| CB[Resilience4j Circuit Breaker]
    end
    
    subgraph Distributed Transaction Saga
        CB -.-> |Timeout/Fail| Fallback(Fallback Handler)
        CB --> |Forward Request| OrderService(Order Service)
        
        subgraph Transactional Outbox Pattern
            OrderService --> |Local TX: Save Order| OrderDB[(PostgreSQL)]
            OrderDB --> |CDC / Polling| MessageRelay[Message Relay]
        end
        
        MessageRelay --> |1. Publish: order-created| Kafka[Apache Kafka]
        
        Kafka --> |2. Consume| InventoryService(Inventory Service)
        InventoryService --> |3a. Publish: inventory-failed| Kafka
        InventoryService --> |3b. Publish: inventory-reserved| Kafka
        
        Kafka --> |4. Consume| PaymentService(Payment Service)
        PaymentService --> |5a. Publish: payment-success| Kafka
        PaymentService --> |5b. Publish: payment-failed| Kafka
        
        Kafka -.-> |6. Update Status (Success/Fail)| OrderService
        Kafka -.-> |6. Compensating Action (Restock)| InventoryService
    end
```
🧠 Core System Design Patterns
This platform is built on enterprise-standard patterns to solve common distributed system challenges:

1. The Transactional Outbox Pattern
   The Problem: The "Dual-Write" problem. If the Order Service saves an order to the database and then publishes a message to Kafka, the network could fail in between, leaving the database and the message broker permanently out of sync.
   The Solution: The service uses a single local database transaction to write both the Order entity and an OutboxEvent entity. A separate background process reliably reads the outbox table and guarantees at-least-once delivery to Kafka, ensuring total eventual consistency.

2. The Saga Pattern (Inventory-First Choreography)
   The Problem: Microservices cannot use traditional ACID transactions (Two-Phase Commits) across multiple databases without locking the entire system. Furthermore, charging a customer's credit card before confirming inventory leads to costly and frustrating refund processes.
   The Solution: Distributed transactions are handled via a strict Event-Driven Choreography Saga.

The Relay: The Order Service publishes an event. The Inventory Service consumes it, reserves the item, and publishes a confirmation. Only then does the Payment Service consume the reservation event to charge the user.

Compensating Transactions: If the payment fails, a compensating event is broadcasted. This triggers the Order Service to securely cancel the order OUT_OF_STOCK or CANCELLED, and simultaneously triggers the Inventory Service to seamlessly restock the reserved item, guaranteeing total data consistency.

3. API Gateway Offloading
   The Problem: Replicating security, CORS, and routing logic across every single microservice creates massive technical debt and slows down development.
   The Solution: The Spring Cloud Gateway acts as the single entry point. It enforces a "Zero-Trust" edge by intercepting JWTs, cryptographically verifying them, extracting the user identity, and injecting it into a secure internal header (X-User-Id) before routing the traffic into the private VPC.

4. Distributed Rate Limiting
   The Problem: Malicious actors or runaway client scripts can easily DDoS downstream microservices like the Order API.
   The Solution: Implemented a Redis-backed Token Bucket algorithm at the Gateway level. Instead of limiting by IP address (which harms users on shared networks), the Gateway extracts the X-User-Id from the JWT and tracks bursts and request rates per individual user, returning 429 Too Many Requests instantly to protect backend CPU cycles.

5. Circuit Breaking & Fallbacks
   The Problem: If the Inventory Service experiences a database lock or a slow cold-start, requests queue up in the Gateway, eventually exhausting server threads and causing cascading system failure.
   The Solution: Wrapped downstream HTTP calls in Resilience4j Circuit Breakers. If a service exceeds a 5-second timeout threshold or fails repeatedly, the circuit opens, instantly failing-fast and routing the user to a graceful fallback endpoint until the downstream service recovers.

💻 Tech Stack
Core: Java 17, Spring Boot 3.3.0, Spring WebFlux (Reactive Gateway)

Architecture Components: Spring Cloud Gateway, Spring Cloud Stream, Resilience4j, Spring Security (JJWT)

Messaging & Data: Apache Kafka, Zookeeper, PostgreSQL, Redis

DevOps: Gradle (Native BOM versioning), Docker, Docker Compose

🛠️ Local Development (Hybrid Workflow)
This project is optimized for developer experience, bypassing the slow "build-destroy-rebuild" Docker loop for Java applications.

1. Spin Up Infrastructure
   Launch the backing services (Databases, Message Brokers) using the customized Docker Compose profile:

Bash: docker-compose --profile infra up -d
2. Run Applications Locally
   Run the Spring Boot applications natively to take advantage of sub-second hot-reloading via spring-boot-devtools:

Bash: ./gradlew bootRun