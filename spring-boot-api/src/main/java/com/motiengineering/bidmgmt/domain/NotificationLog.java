package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row per reminder/digest actually sent - see V5's migration comment for why this exists (idempotency across scheduler re-runs). */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ReminderType reminderType;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private UUID entityId;

    private String recipientEmail;

    private LocalDate reminderDate;

    @CreationTimestamp
    private Instant sentAt;

    public NotificationLog(ReminderType reminderType, EntityType entityType, UUID entityId, String recipientEmail, LocalDate reminderDate) {
        this.reminderType = reminderType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.recipientEmail = recipientEmail;
        this.reminderDate = reminderDate;
    }
}
