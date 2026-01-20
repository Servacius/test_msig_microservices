package com.order.service.model;

public enum OrderStatus {
    CREATED,           // Order created
    PAYMENT_PENDING,   // Waiting for payment
    PAYMENT_PROCESSING,// Payment in progress
    PAID,              // Payment successful
    PAYMENT_FAILED,    // Payment failed
    CANCELLED          // Order cancelled
}