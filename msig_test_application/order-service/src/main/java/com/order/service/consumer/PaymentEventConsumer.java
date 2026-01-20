package com.order.service.consumer;

import com.order.service.event.PaymentEvent;
import com.order.service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {
    
    private final OrderService orderService;
    
    @KafkaListener(
        topics = "payment-events",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvent(PaymentEvent event, Acknowledgment ack) {
        log.info("Consuming payment event: {} for payment: {}", 
            event.getEventType(), event.getPaymentId());
        
        try {
            // Process event idempotently
            orderService.handlePaymentEvent(event);
            
            // Acknowledge only after successful processing
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment event: {}", event.getPaymentId(), e);
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }
}