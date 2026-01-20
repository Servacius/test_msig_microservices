package com.payment.service.exception;

public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
    public PaymentGatewayException(Throwable cause) {
        super(cause);
    }
    
    public PaymentGatewayException(String message) {
        super(message);
    }
}