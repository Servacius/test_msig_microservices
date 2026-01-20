package com.order.service.dto;

import lombok.Data;

@Data
public class PaymentResponse {
    private String paymentId;
    private String status;
}
