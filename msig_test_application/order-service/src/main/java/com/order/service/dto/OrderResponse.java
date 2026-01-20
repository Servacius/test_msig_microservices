package com.order.service.dto;

import com.order.service.model.OrderStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
    private String currency;
    private OrderStatus status;
    private String paymentId;
    private LocalDateTime createdAt;
}