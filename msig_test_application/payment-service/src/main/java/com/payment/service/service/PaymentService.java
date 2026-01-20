package com.payment.service.service;
import com.payment.service.client.PaymentGatewayClient;
import com.payment.service.dto.*;
import com.payment.service.exception.*;
import com.payment.service.model.*;
import com.payment.service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import com.payment.service.event.PaymentEvent;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentCallbackRepository callbackRepository;
    private final PaymentGatewayClient gatewayClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Create payment with idempotency
     * CRITICAL: Prevents double charging even if called multiple times
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("Creating payment for order: {}, idempotencyKey: {}", 
            request.getOrderId(), request.getIdempotencyKey());
        
        // Check idempotency - if exists, return existing payment
        Optional<Payment> existing = paymentRepository
            .findByIdempotencyKey(request.getIdempotencyKey());
        
        if (existing.isPresent()) {
            log.warn("Duplicate payment request detected. IdempotencyKey: {}", 
                request.getIdempotencyKey());
            return mapToResponse(existing.get());
        }
        
        // Create new payment
        Payment payment = Payment.builder()
            .paymentId(generatePaymentId())
            .orderId(request.getOrderId())
            .idempotencyKey(request.getIdempotencyKey())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .status(PaymentStatus.PENDING)
            .version(0)
            .build();
        
        payment = paymentRepository.save(payment);
        log.info("Payment created: {}", payment.getPaymentId());
        
        // Process payment asynchronously (to handle timeouts)
        processPaymentAsync(payment);
        
        return mapToResponse(payment);
    }
    
    /**
     * Process payment with retry logic for network timeouts
     */
    @Retryable(
        value = {NetworkTimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void processPaymentAsync(Payment payment) {
        log.info("Processing payment: {}", payment.getPaymentId());
        
        try {
            // Update status to PROCESSING
            updatePaymentStatus(payment.getPaymentId(), PaymentStatus.PROCESSING);
            
            // Call payment gateway with timeout handling
            GatewayPaymentRequest gatewayRequest = GatewayPaymentRequest.builder()
                .paymentReference(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
            
            GatewayPaymentResponse gatewayResponse = 
                gatewayClient.processPayment(gatewayRequest);
            
            // Update payment with gateway reference
            Payment updated = paymentRepository.findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentId()));
            
            updated.setGatewayReference(gatewayResponse.getTransactionId());
            paymentRepository.save(updated);
            
            log.info("Payment sent to gateway: {}, gatewayRef: {}", 
                payment.getPaymentId(), gatewayResponse.getTransactionId());
            
            // Note: Don't update to SUCCESS here
            // Wait for callback from gateway
            
        } catch (Exception e) {
            log.error("Payment processing failed: {}", payment.getPaymentId(), e);
            updatePaymentStatus(payment.getPaymentId(), PaymentStatus.FAILED, 
                e.getMessage());
            
            // Publish failure event
            publishPaymentEvent(payment.getPaymentId(), "PAYMENT_FAILED");
            
            throw new PaymentProcessingException("Failed to process payment", e);
        }
    }
    
    /**
     * Handle callback from payment gateway
     * CRITICAL: Must be idempotent - gateway may send callback multiple times
     */
    @Transactional
    public void handleCallback(PaymentCallbackRequest request) {
        log.info("Received payment callback: {}, paymentRef: {}", 
            request.getCallbackId(), request.getPaymentReference());
        
        // 1. Check if callback already processed (idempotency)
        if (callbackRepository.existsByCallbackId(request.getCallbackId())) {
            log.warn("Duplicate callback detected: {}. Ignoring.", 
                request.getCallbackId());
            return; // Already processed, skip
        }
        
        // 2. Store callback for audit trail
        PaymentCallback callback = new PaymentCallback();
        callback.setCallbackId(request.getCallbackId());
        callback.setPaymentReference(request.getPaymentReference());
        callback.setStatus(request.getStatus());
        callback.setRawPayload(request.toString());
        callback.setProcessed(false);
        
        callbackRepository.save(callback);
        
        // 3. Lock payment row to prevent concurrent updates
        Payment payment = paymentRepository
            .findByPaymentIdWithLock(request.getPaymentReference())
            .orElseThrow(() -> new PaymentNotFoundException(
                request.getPaymentReference()));
        
        // 4. Check if payment is in valid state for update
        if (payment.getStatus() == PaymentStatus.SUCCESS || 
            payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("Payment {} already in terminal state: {}. Ignoring callback.", 
                payment.getPaymentId(), payment.getStatus());
            callback.setProcessed(true);
            callbackRepository.save(callback);
            return;
        }
        
        // 5. Update payment status based on callback
        PaymentStatus newStatus = mapGatewayStatus(request.getStatus());
        payment.setStatus(newStatus);
        
        if (request.getTransactionId() != null) {
            payment.setGatewayReference(request.getTransactionId());
        }
        
        if (request.getFailureReason() != null) {
            payment.setFailureReason(request.getFailureReason());
        }
        
        paymentRepository.save(payment);
        
        // 6. Mark callback as processed
        callback.setProcessed(true);
        callbackRepository.save(callback);
        
        log.info("Payment {} updated to status: {}", 
            payment.getPaymentId(), newStatus);
        
        // 7. Publish event to notify other services
        String eventType = newStatus == PaymentStatus.SUCCESS ? 
            "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        publishPaymentEvent(payment.getPaymentId(), eventType);
    }
    
    /**
     * Update payment status with optimistic locking
     */
    @Transactional
    public void updatePaymentStatus(String paymentId, PaymentStatus status) {
        updatePaymentStatus(paymentId, status, null);
    }
    
    @Transactional
    public void updatePaymentStatus(String paymentId, PaymentStatus status, 
                                    String failureReason) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        
        payment.setStatus(status);
        if (failureReason != null) {
            payment.setFailureReason(failureReason);
        }
        
        paymentRepository.save(payment);
    }
    
    /**
     * Publish payment event to Kafka
     */
    private void publishPaymentEvent(String paymentId, String eventType) {
        try {
            PaymentEvent event = PaymentEvent.builder()
                .paymentId(paymentId)
                .eventType(eventType)
                .timestamp(System.currentTimeMillis())
                .build();
            
            kafkaTemplate.send("payment-events", paymentId, event);
            log.info("Published event: {} for payment: {}", eventType, paymentId);
            
        } catch (Exception e) {
            log.error("Failed to publish event for payment: {}", paymentId, e);
            // Don't throw - event publishing failure shouldn't fail payment
        }
    }
    
    private String generatePaymentId() {
        return "PAY-" + UUID.randomUUID().toString();
    }
    
    private PaymentStatus mapGatewayStatus(String gatewayStatus) {
        return switch (gatewayStatus.toUpperCase()) {
            case "SUCCESS", "COMPLETED" -> PaymentStatus.SUCCESS;
            case "FAILED", "DECLINED" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PROCESSING;
        };
    }
    public PaymentResponse getPayment(String paymentId) {
        log.debug("Fetching payment: {}", paymentId);
    
        Payment payment = paymentRepository.findByPaymentId(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    
        return mapToResponse(payment);
    }
    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
            .paymentId(payment.getPaymentId())
            .orderId(payment.getOrderId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .gatewayReference(payment.getGatewayReference())
            .createdAt(payment.getCreatedAt())
            .failureReason(payment.getFailureReason())
            .build();
    }
}