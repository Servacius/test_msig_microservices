package com.payment.service.event;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentEvent {
    private String paymentId;
    private String orderId;
    private String eventType; // PAYMENT_SUCCESS, PAYMENT_FAILED
    private BigDecimal amount;
    private String currency;
    private Long timestamp;
    private Integer version;
}