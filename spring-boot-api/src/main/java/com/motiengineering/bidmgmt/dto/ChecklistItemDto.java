package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChecklistItemDto(
        UUID id,
        String title,
        UUID ownerId,
        String ownerName,
        LocalDate dueDate,
        boolean done,
        Instant doneAt,
        String doneByName) {
}
