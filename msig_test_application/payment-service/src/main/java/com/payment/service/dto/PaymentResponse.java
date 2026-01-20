package com.payment.service.dto;

import com.payment.service.model.PaymentStatus;
import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String gatewayReference;
    private LocalDateTime createdAt;
    private String failureReason;
}
