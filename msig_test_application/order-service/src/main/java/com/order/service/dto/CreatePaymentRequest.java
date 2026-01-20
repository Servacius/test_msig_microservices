package com.order.service.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
}
