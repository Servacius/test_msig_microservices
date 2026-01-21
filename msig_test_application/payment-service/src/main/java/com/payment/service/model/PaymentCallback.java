package com.payment.service.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_callbacks", indexes = {
    @Index(name = "idx_callback_idempotency", columnList = "callbackId", unique = true),
    @Index(name = "idx_payment_ref", columnList = "paymentReference")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallback {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String callbackId; // Unique callback identifier
    
    @Column(nullable = false)
    private String paymentReference;
    
    @Column(nullable = false)
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String rawPayload; // Store entire callback for audit
    
    @Column(nullable = false)
    private LocalDateTime receivedAt;
    
    @Column(nullable = false)
    private Boolean processed;
    
    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
        if (processed == null) {
            processed = false;
        }
    }
}