package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** All fields optional - only non-null fields are applied (a PATCH-style update). */
public record UpdateBidRequest(
        String title,
        String referenceNumber,
        String source,
        LocalDate scoutedDate,
        List<String> scoutNames,
        Instant closingAt,
        Instant openingAt,
        Instant clarificationDeadline,
        Integer bidValidityDays,
        String goNoGoDecision,
        String goNoGoReason,
        String notes) {
}
