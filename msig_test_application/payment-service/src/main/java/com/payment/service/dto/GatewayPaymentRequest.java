package com.payment.service.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class GatewayPaymentRequest {
    private String paymentReference;
    private BigDecimal amount;
    private String currency;
    private String callbackUrl; // Where gateway sends callback
}