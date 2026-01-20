package com.notification.service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    
    /**
     * Send email via external service (e.g., SendGrid, SES)
     */
    public void sendEmail(String recipient, String subject, String content) {
        log.info("Sending email to: {}", recipient);
        log.info("Subject: {}", subject);
        log.info("Content: {}", content);
        
        // In real implementation:
        // - Call SendGrid/AWS SES/other email service
        // - Handle timeouts and retries
        // - Validate recipient email
        
        // Simulate email sending
        try {
            Thread.sleep(100); // Simulate network call
            log.info("Email sent successfully to: {}", recipient);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted", e);
        }
    }
}