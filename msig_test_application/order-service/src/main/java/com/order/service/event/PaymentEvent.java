package com.order.service.event;

import lombok.Data;

@Data
public class PaymentEvent {
    private String eventId;        // UNIQUE per event
    private String paymentId;
    private String orderId;
    private String eventType;      // PAYMENT_SUCCESS | PAYMENT_FAILED
    private Integer version;
    private Long timestamp;
}
