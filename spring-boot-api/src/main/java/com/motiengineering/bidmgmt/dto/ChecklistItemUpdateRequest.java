package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ChecklistItemUpdateRequest(Boolean done, UUID ownerId, LocalDate dueDate) {
}
