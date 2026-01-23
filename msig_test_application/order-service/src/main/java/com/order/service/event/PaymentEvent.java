package com.order.service.event;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String paymentId;
    private String orderId;
    private String eventType;
    private BigDecimal amount;
    private String currency;
    private Long timestamp;
    private Integer version;
}