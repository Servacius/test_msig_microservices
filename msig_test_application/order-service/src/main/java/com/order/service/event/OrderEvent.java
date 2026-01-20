package com.order.service.event;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class OrderEvent {
    private String orderId;
    private String userId;
    private String eventType;
    private BigDecimal totalAmount;
    private String currency;
    private Long timestamp;
}
