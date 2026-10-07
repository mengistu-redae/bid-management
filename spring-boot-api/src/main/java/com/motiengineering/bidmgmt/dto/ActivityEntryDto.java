package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityEntryDto(UUID id, String authorName, String note, Instant createdAt) {
}
