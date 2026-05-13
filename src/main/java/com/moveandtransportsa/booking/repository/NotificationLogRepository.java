package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    @Query("SELECT n FROM NotificationLog n WHERE n.status IN ('PENDING', 'FAILED') AND n.attemptCount < :maxAttempts")
    List<NotificationLog> findRetryable(@Param("maxAttempts") int maxAttempts);
}
