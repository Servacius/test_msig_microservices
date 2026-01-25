package com.order.service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.service.client.PaymentServiceClient;
import com.order.service.dto.*;
import com.order.service.event.OrderEvent;
import com.order.service.event.PaymentEvent;
import com.order.service.exception.PaymentInitiationException;
import com.order.service.exception.OrderNotFoundException;
import com.order.service.model.*;
import com.order.service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());
        
        //  Total
        BigDecimal totalAmount = request.getItems().stream()
            .map(item -> item.getPrice().multiply(
                BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Create order
        Order order = Order.builder()
            .orderId(generateOrderId())
            .userId(request.getUserId())
            .totalAmount(totalAmount)
            .currency(request.getCurrency())
            .status(OrderStatus.CREATED)
            .items(serializeItems(request.getItems()))
            .version(0)
            .build();
        
        order = orderRepository.save(order);
        log.info("Order created: {}", order.getOrderId());
        
        // Update status ke payment pending
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);
        
        // Initiate payment secara asynchronous
        initiatePayment(order);
        
        return mapToResponse(order);
    }
    
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void initiatePayment(Order order) {
        log.info("Initiating payment for order: {}", order.getOrderId());
        
        try {
            // Generate idempotency key
            String idempotencyKey = generatePaymentIdempotencyKey(order.getOrderId());
            
            // Create payment request
            CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
            paymentRequest.setOrderId(order.getOrderId());
            paymentRequest.setAmount(order.getTotalAmount());
            paymentRequest.setCurrency(order.getCurrency());
            paymentRequest.setIdempotencyKey(idempotencyKey);
            
            PaymentResponse paymentResponse = paymentClient.createPayment(
                paymentRequest, idempotencyKey);
            
            // Update order with payment reference
            Order updated = orderRepository.findByOrderId(order.getOrderId())
                .orElseThrow();
            updated.setPaymentId(paymentResponse.getPaymentId());
            updated.setStatus(OrderStatus.PAYMENT_PROCESSING);
            orderRepository.save(updated);
            
            log.info("Payment initiated for order: {}, paymentId: {}", 
                order.getOrderId(), paymentResponse.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to initiate payment for order: {}", 
                order.getOrderId(), e);
            
            // Update order status to payment failed
            Order updated = orderRepository.findByOrderId(order.getOrderId())
                .orElseThrow();
            updated.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(updated);
            
            throw new PaymentInitiationException(
                "Failed to initiate payment", e);
        }
    }
    
    /**
     * Handle payment events from Kafka
     */
    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Received payment event: {} for payment: {}", 
            event.getEventType(), event.getPaymentId());
        
        // Find order by payment ID
        Order order = orderRepository.findByPaymentId(event.getPaymentId())
            .orElseGet(() -> {
                log.warn("No order found for payment: {}", event.getPaymentId());
                return null;
            });
        
        if (order == null) {
            return;
        }
        
        // Lock order to prevent concurrent updates
        order = orderRepository.findByOrderIdWithLock(order.getOrderId())
            .orElseThrow();
        
        // Check version to prevent processing old events
        if (event.getVersion() != null && 
            order.getVersion() >= event.getVersion()) {
            log.warn("Ignoring old payment event. Order version: {}, Event version: {}", 
                order.getVersion(), event.getVersion());
            return;
        }
        
        // Update order status based on payment event
        switch (event.getEventType()) {
            case "PAYMENT_SUCCESS":
                if (order.getStatus() != OrderStatus.PAID) {
                    order.setStatus(OrderStatus.PAID);
                    orderRepository.save(order);
                    
                    log.info("Order {} marked as PAID", order.getOrderId());
                    
                    // Publish order event
                    publishOrderEvent(order, "ORDER_PAID");
                }
                break;
                
            case "PAYMENT_FAILED":
                if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
                    order.setStatus(OrderStatus.PAYMENT_FAILED);
                    orderRepository.save(order);
                    
                    log.info("Order {} marked as PAYMENT_FAILED", order.getOrderId());
                    
                    // Publish order event
                    publishOrderEvent(order, "ORDER_PAYMENT_FAILED");
                }
                break;
        }
    }
    
    /**
     * Get order by ID
     */
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        return mapToResponse(order);
    }
    
    /**
     * Publish order event to Kafka
     */
    private void publishOrderEvent(Order order, String eventType) {
        try {
            OrderEvent event = OrderEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .eventType(eventType)
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .timestamp(System.currentTimeMillis())
                .build();
            
            kafkaTemplate.send("order-events", order.getOrderId(), event);
            log.info("Published event: {} for order: {}", eventType, order.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to publish event for order: {}", order.getOrderId(), e);
        }
    }
    
    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString();
    }
    
    private String generatePaymentIdempotencyKey(String orderId) {
        // Consistent idempotency key based on order ID
        // Same order always generates same key
        return "PAY-IDEMPOTENCY-" + orderId;
    }
    
    private String serializeItems(List<CreateOrderRequest.OrderItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize items", e);
        }
    }

    public List<OrderResponse> getOrdersByUser(String userId) {
    log.info("Getting orders for user: {}", userId);
    
    List<Order> orders = orderRepository.findByUserId(userId);
    
    return orders.stream()
        .map(this::mapToResponse)
        .collect(java.util.stream.Collectors.toList());
}
    
    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .userId(order.getUserId())
            .totalAmount(order.getTotalAmount())
            .currency(order.getCurrency())
            .status(order.getStatus())
            .paymentId(order.getPaymentId())
            .createdAt(order.getCreatedAt())
            .build();
    }
}