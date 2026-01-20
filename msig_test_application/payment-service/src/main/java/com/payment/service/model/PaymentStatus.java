package com.payment.service.model;

public enum PaymentStatus {
    PENDING,      // Initial state
    PROCESSING,   // Sent to gateway
    SUCCESS,      // Confirmed by gateway
    FAILED,       // Failed
    REFUNDED      // Refunded
}