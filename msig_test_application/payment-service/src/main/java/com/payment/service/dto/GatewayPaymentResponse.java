package com.payment.service.dto;

import lombok.Data;

@Data
public class GatewayPaymentResponse {
    private String transactionId;
    private String status;
    private String message;
}