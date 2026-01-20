package com.payment.service.exception;

public class NetworkTimeoutException extends RuntimeException {
    public NetworkTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}