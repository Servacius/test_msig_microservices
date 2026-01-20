package com.notification.service.service;

import com.notification.service.model.*;
import com.notification.service.event.OrderEvent;
import com.notification.service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationLogRepository notificationRepository;
    private final EmailService emailService;
    
    /**
     * Process order event and send notification
     * Idempotent - checks if event already processed
     */
    @Transactional
    public void processOrderEvent(OrderEvent event) {
        log.info("Processing order event: {} for order: {}", 
            event.getEventType(), event.getOrderId());
        
        // Generate unique event ID
        String eventId = generateEventId(event);
        
        // Check if already processed (idempotency)
        if (notificationRepository.existsByEventId(eventId)) {
            log.warn("Event already processed: {}. Skipping.", eventId);
            return;
        }
        
        // Create notification based on event type
        NotificationLog notification = null;
        
        switch (event.getEventType()) {
            case "ORDER_PAID":
                notification = createOrderPaidNotification(event, eventId);
                break;
                
            case "ORDER_PAYMENT_FAILED":
                notification = createPaymentFailedNotification(event, eventId);
                break;
                
            default:
                log.warn("Unknown event type: {}", event.getEventType());
                return;
        }
        
        // Save notification log
        notification = notificationRepository.save(notification);
        
        // Send notification asynchronously
        sendNotificationAsync(notification);
    }
    
    /**
     * Send notification with retry logic
     */
    @Async
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public void sendNotificationAsync(NotificationLog notification) {
        log.info("Sending notification: {}", notification.getId());
        
        try {
            // Send based on type
            switch (notification.getType()) {
                case EMAIL:
                    emailService.sendEmail(
                        notification.getRecipient(),
                        notification.getSubject(),
                        notification.getContent()
                    );
                    break;
                    
                case SMS:
                    // SMS implementation
                    break;
                    
                case PUSH:
                    // Push notification implementation
                    break;
            }
            
            // Update status to SENT
            updateNotificationStatus(notification.getId(), 
                NotificationStatus.SENT, null);
            
            log.info("Notification sent successfully: {}", notification.getId());
            
        } catch (Exception e) {
            log.error("Failed to send notification: {}", notification.getId(), e);
            
            // Update status to FAILED
            updateNotificationStatus(notification.getId(), 
                NotificationStatus.FAILED, e.getMessage());
            
            // Increment retry count
            incrementRetryCount(notification.getId());
            
            throw e; // Re-throw for retry mechanism
        }
    }
    
    @Transactional
    protected void updateNotificationStatus(Long id, NotificationStatus status, 
                                           String errorMessage) {
        NotificationLog notification = notificationRepository.findById(id)
            .orElseThrow();
        
        notification.setStatus(status);
        notification.setErrorMessage(errorMessage);
        
        if (status == NotificationStatus.SENT) {
            notification.setSentAt(LocalDateTime.now());
        }
        
        notificationRepository.save(notification);
    }
    
    @Transactional
    protected void incrementRetryCount(Long id) {
        NotificationLog notification = notificationRepository.findById(id)
            .orElseThrow();
        
        notification.setRetryCount(notification.getRetryCount() + 1);
        notificationRepository.save(notification);
    }
    
    private NotificationLog createOrderPaidNotification(OrderEvent event, 
                                                        String eventId) {
        return NotificationLog.builder()
            .eventId(eventId)
            .userId(event.getUserId())
            .orderId(event.getOrderId())
            .type(NotificationType.EMAIL)
            .recipient(getUserEmail(event.getUserId()))
            .subject("Order Confirmed - " + event.getOrderId())
            .content(buildOrderPaidEmailContent(event))
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();
    }
    
    private NotificationLog createPaymentFailedNotification(OrderEvent event, 
                                                            String eventId) {
        return NotificationLog.builder()
            .eventId(eventId)
            .userId(event.getUserId())
            .orderId(event.getOrderId())
            .type(NotificationType.EMAIL)
            .recipient(getUserEmail(event.getUserId()))
            .subject("Payment Failed - " + event.getOrderId())
            .content(buildPaymentFailedEmailContent(event))
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();
    }
    
    private String generateEventId(OrderEvent event) {
        // Generate unique ID based on event data
        return String.format("%s-%s-%d", 
            event.getEventType(), 
            event.getOrderId(), 
            event.getTimestamp()
        );
    }
    
    private String getUserEmail(String userId) {
        // In real implementation, fetch from user service
        return userId + "@example.com";
    }
    
    private String buildOrderPaidEmailContent(OrderEvent event) {
        return String.format("""
            Dear Customer,
            
            Your order %s has been confirmed!
            
            Order Details:
            - Order ID: %s
            - Amount: %s %s
            
            Thank you for your purchase!
            
            Best regards,
            Your Store
            """,
            event.getOrderId(),
            event.getOrderId(),
            event.getTotalAmount(),
            event.getCurrency()
        );
    }
    
    private String buildPaymentFailedEmailContent(OrderEvent event) {
        return String.format("""
            Dear Customer,
            
            We were unable to process payment for order %s.
            
            Order Details:
            - Order ID: %s
            - Amount: %s %s
            
            Please try again or contact support.
            
            Best regards,
            Your Store
            """,
            event.getOrderId(),
            event.getOrderId(),
            event.getTotalAmount(),
            event.getCurrency()
        );
    }
}