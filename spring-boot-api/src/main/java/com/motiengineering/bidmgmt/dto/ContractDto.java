package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractDto(
        UUID id,
        UUID bidId,
        String bidTitle,
        String contractNumber,
        String status,
        Instant signedAt,
        LocalDate deliveryDeadline,
        Instant completedAt,
        String exitReason,
        String notes,
        boolean onHold,
        Instant createdAt,
        Instant updatedAt) {
}
