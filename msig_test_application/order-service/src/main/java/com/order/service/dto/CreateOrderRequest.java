package com.order.service.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotEmpty(message = "Order items cannot be empty")
    private List<OrderItem> items;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @Data
    public static class OrderItem {
        @NotBlank
        private String productId;
        
        @NotBlank
        private String productName;
        
        @NotNull
        @Min(1)
        private Integer quantity;
        
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal price;
    }
}