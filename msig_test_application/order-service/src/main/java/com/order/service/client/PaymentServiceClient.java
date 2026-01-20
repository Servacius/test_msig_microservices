package com.order.service.client;

import com.order.service.dto.CreateOrderRequest;
import com.order.service.dto.CreatePaymentRequest;
import com.order.service.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "payment-service",
    url = "${payment.service.url}"
)
public interface PaymentServiceClient {
    
    @PostMapping("/api/payments")
    @CircuitBreaker(name = "paymentService")
    PaymentResponse createPayment(
        @RequestBody CreatePaymentRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    );
}