package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.NotificationLog;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Sends a reminder/digest through every channel that supports the
 * recipient (just email for v1 - see NotificationChannel's javadoc),
 * recording a NotificationLog row per successful send so the same
 * (reminder type, entity, recipient, day) is never sent twice even if
 * ReminderSchedulerService's job runs more than once in a day.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final List<NotificationChannel> channels;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${bidmgmt.notifications.enabled}")
    private boolean notificationsEnabled;

    @Transactional
    public void sendIfNotAlreadySent(ReminderType type, EntityType entityType, UUID entityId, AppUser recipient, EmailContent content) {
        if (!notificationsEnabled || recipient == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);

        for (NotificationChannel channel : channels) {
            if (!channel.supports(recipient)) {
                continue;
            }
            String dedupKey = recipient.getEmail();
            boolean alreadySent = notificationLogRepository
                    .existsByReminderTypeAndEntityTypeAndEntityIdAndRecipientEmailAndReminderDate(type, entityType, entityId, dedupKey, today);
            if (alreadySent) {
                continue;
            }
            try {
                channel.send(recipient, content.subject(), content.html());
                notificationLogRepository.save(new NotificationLog(type, entityType, entityId, dedupKey, today));
            } catch (NotificationSendException e) {
                log.warn("Failed to send {} to {} for {}/{}: {}", type, dedupKey, entityType, entityId, e.getMessage());
            }
        }
    }
}
