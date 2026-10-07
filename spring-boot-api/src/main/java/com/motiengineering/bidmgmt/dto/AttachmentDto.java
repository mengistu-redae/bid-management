package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentDto(UUID id, String filename, String contentType, Long sizeBytes, String uploadedByName, Instant uploadedAt) {
}
