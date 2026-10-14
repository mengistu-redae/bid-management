package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DelayBoardRowDto(
        UUID bidId,
        String bidTitle,
        UUID lotId,
        String lotLabel,
        UUID organizationId,
        String organizationName,
        UUID divisionId,
        String divisionName,
        String status,
        String oemBrand,
        String indicatorLevel,
        Double percentUsed,
        Double daysUsed,
        Double targetDays,
        boolean stale,
        String waitingOnParty,
        List<String> officerNames,
        LocalDate nextFollowUpDate,
        String followUpOwnerName) {
}
