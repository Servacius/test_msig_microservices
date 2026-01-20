package com.notification.service.consumer;

import com.notification.service.event.OrderEvent;
import com.notification.service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {
    
    private final NotificationService notificationService;
    
    @KafkaListener(
        topics = "order-events",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(OrderEvent event, Acknowledgment ack) {
        log.info("Consuming order event: {} for order: {}", 
            event.getEventType(), event.getOrderId());
        
        try {
            // Process event idempotently
            notificationService.processOrderEvent(event);
            
            // Acknowledge only after successful processing
            ack.acknowledge();
            
            log.info("Order event processed successfully: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("Error processing order event: {}", event.getOrderId(), e);
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }
}