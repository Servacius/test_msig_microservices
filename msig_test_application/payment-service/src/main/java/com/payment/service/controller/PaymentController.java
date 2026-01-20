package com.payment.service.controller;

import com.payment.service.dto.*;
import com.payment.service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentService paymentService;
    
    /**
     * Create payment with idempotency
     * Idempotency-Key header prevents double charging
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Received payment request for order: {}", request.getOrderId());
        
        // Use header idempotency key if provided, otherwise use from body
        if (idempotencyKey != null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        
        PaymentResponse response = paymentService.createPayment(request);
        
        // Return 200 for existing payment (idempotent), 201 for new
        HttpStatus status = response.getCreatedAt().isBefore(
            java.time.LocalDateTime.now().minusSeconds(5)) 
            ? HttpStatus.OK : HttpStatus.CREATED;
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Webhook endpoint for payment gateway callbacks
     * CRITICAL: Must handle duplicate callbacks
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @Valid @RequestBody PaymentCallbackRequest request) {
        
        log.info("Received callback: {} for payment: {}", 
            request.getCallbackId(), request.getPaymentReference());
        
        try {
            paymentService.handleCallback(request);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Error processing callback: {}", request.getCallbackId(), e);
            // Return 200 anyway to prevent gateway from retrying
            // We've stored the callback for manual review
            return ResponseEntity.ok().build();
        }
    }
    
    /**
     * Get payment status
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        log.info("Getting payment: {}", paymentId);
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }
}