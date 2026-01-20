package com.payment.service.repository;

import com.payment.service.model.Payment;
import com.payment.service.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    Optional<Payment> findByPaymentId(String paymentId);
    
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    
    Optional<Payment> findByOrderId(String orderId);
    
    // Pessimistic lock for preventing concurrent updates
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByPaymentIdWithLock(@Param("paymentId") String paymentId);
    
    boolean existsByIdempotencyKey(String idempotencyKey);
}