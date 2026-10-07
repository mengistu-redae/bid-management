package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryDto(UUID id, String fromStatus, String toStatus, String reason, String changedByName, Instant changedAt) {
}
