package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * entityType/entityId/detailHref are the generic identity - added so the
 * Delays board can show Contract and Logistics rows alongside BidLot ones.
 * bidId/lotId stay null for Contract/Logistics rows; bidTitle/lotLabel are
 * reused as a generic title/subtitle pair for every row type rather than
 * adding yet another pair of fields, so the existing node-bff templates'
 * display logic didn't need to change, only their link/form-target logic.
 */
public record DelayBoardRowDto(
        String entityType,
        UUID entityId,
        String detailHref,
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
        String followUpOwnerName,
        /** The owning bid's closing date - null for Contract/Logistics/Opportunity/DealRegistration rows, which have no bid-closing concept. */
        Instant closingAt) {
}
