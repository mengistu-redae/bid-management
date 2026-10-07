package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.NotificationLog;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    boolean existsByReminderTypeAndEntityTypeAndEntityIdAndRecipientEmailAndReminderDate(
            ReminderType reminderType, EntityType entityType, UUID entityId, String recipientEmail, LocalDate reminderDate);
}
