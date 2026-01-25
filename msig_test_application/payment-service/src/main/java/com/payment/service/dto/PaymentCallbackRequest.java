package com.payment.service.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PaymentCallbackRequest {
    
    @NotBlank
    private String callbackId;
    
    @NotBlank
    private String paymentReference;
    
    @NotBlank
    private String status;
    
    private String transactionId;
    
    private String failureReason;
    
    private String signature;
}