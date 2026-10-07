package com.motiengineering.bidmgmt.dto;

import java.time.Instant;
import java.util.UUID;

/** All fields optional - null means "don't filter on this". Used by both the filtered list screen and Excel export, so the two always agree on what's included. */
public record BidFilter(
        UUID divisionId,
        String status,
        UUID officerId,
        UUID organizationId,
        Instant closingFrom,
        Instant closingTo) {

    public static BidFilter empty() {
        return new BidFilter(null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return divisionId == null && status == null && officerId == null && organizationId == null && closingFrom == null && closingTo == null;
    }
}
