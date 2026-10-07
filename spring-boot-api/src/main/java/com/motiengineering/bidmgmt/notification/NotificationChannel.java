package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;

/**
 * One delivery channel for a notification. Email is the only implementation
 * for v1 (per the brief); a Telegram bot channel can be added later by
 * implementing this same interface against a `telegramChatId` field on
 * AppUser (not yet added - there's nowhere to collect it in the UI yet) and
 * registering it as another Spring bean - NotificationService already
 * fans out to every NotificationChannel bean it's given, so no other
 * wiring changes would be needed.
 */
public interface NotificationChannel {

    /** Whether this channel has enough contact info to reach this recipient (e.g. a non-null email). */
    boolean supports(AppUser recipient);

    void send(AppUser recipient, String subject, String htmlBody);
}
