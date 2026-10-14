package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

public record StageHistoryEntryDto(
        UUID id,
        String stageField,
        String fromValue,
        String toValue,
        String note,
        String changedByName,
        Instant changedAt,
        boolean backfilled) {
}
