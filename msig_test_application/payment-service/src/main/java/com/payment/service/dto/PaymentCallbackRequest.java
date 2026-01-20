package com.payment.service.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PaymentCallbackRequest {
    
    @NotBlank
    private String callbackId; // Gateway provides unique callback ID
    
    @NotBlank
    private String paymentReference; // Our payment ID
    
    @NotBlank
    private String status; // SUCCESS, FAILED, etc.
    
    private String transactionId; // Gateway's transaction ID
    
    private String failureReason;
    
    private String signature; // For webhook verification
}
