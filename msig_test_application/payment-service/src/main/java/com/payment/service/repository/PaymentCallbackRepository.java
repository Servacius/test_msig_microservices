package com.payment.service.repository;

import com.payment.service.model.PaymentCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCallbackRepository extends JpaRepository<PaymentCallback, Long> {
    
    Optional<PaymentCallback> findByCallbackId(String callbackId);
    
    boolean existsByCallbackId(String callbackId);
}