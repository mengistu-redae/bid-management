package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FollowUpDto(
        LocalDate nextFollowUpDate,
        UUID followUpOwnerId,
        String followUpOwnerName,
        LocalDate snoozedUntil,
        String snoozeReason,
        Instant updatedAt) {
}
