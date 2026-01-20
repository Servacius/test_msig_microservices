package com.notification.service.repository;

import com.notification.service.model.NotificationLog;
import com.notification.service.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    
    Optional<NotificationLog> findByEventId(String eventId);
    
    boolean existsByEventId(String eventId);
    
    List<NotificationLog> findByStatusAndRetryCountLessThan(
        NotificationStatus status, Integer maxRetries);
}