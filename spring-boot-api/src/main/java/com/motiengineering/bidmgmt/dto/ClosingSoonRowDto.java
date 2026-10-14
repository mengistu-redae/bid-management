package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
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
        List<String> divisionNames,
        /** Division/Owner/Go-no-go/MAF/Vendor quote/Closing time/Verify bond/Scope - whichever of these the bid is still missing. */
        List<String> readinessGaps,
        /** Comma-joined account-officer names across the bid's lots, or null when none are assigned. */
        String ownerLabel,
        BigDecimal bidBondAmountEtb,
        BigDecimal bidBondAmountUsd) {
}
