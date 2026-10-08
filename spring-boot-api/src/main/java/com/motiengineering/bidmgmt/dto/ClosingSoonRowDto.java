package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClosingSoonRowDto(
        UUID bidId,
        String title,
        String organizationName,
        Instant closingAt,
        String status,
        int checklistProgressPercent,
        boolean redFlag,
        String redFlagReason,
        List<String> divisionNames) {
}
