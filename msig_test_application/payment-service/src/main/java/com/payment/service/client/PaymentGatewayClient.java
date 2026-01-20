package com.payment.service.client;

import com.payment.service.config.FeignConfig;
import com.payment.service.dto.GatewayPaymentRequest;
import com.payment.service.dto.GatewayPaymentResponse;
import com.payment.service.exception.PaymentGatewayException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "payment-gateway",
    url = "${payment.gateway.url}",
    configuration = FeignConfig.class
)
public interface PaymentGatewayClient {
    
    @PostMapping("/api/charge")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentGateway")
    GatewayPaymentResponse processPayment(@RequestBody GatewayPaymentRequest request);
    
    // Fallback method when circuit breaker opens
    default GatewayPaymentResponse processPaymentFallback(
            GatewayPaymentRequest request, Exception ex) {
        throw new PaymentGatewayException(
            "Payment gateway unavailable. Will retry later.");
    }
}