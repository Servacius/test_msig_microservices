package com.payment.service.config;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {
    
    @Bean
    public Request.Options requestOptions() {
        // Connect timeout: 3 seconds
        // Read timeout: 10 seconds
        return new Request.Options(
            3, TimeUnit.SECONDS,
            10, TimeUnit.SECONDS,
            true
        );
    }
    
    @Bean
    public Retryer retryer() {
        // Disable Feign's default retry
        // We use @Retry annotation for more control
        return Retryer.NEVER_RETRY;
    }
}