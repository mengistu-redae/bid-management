package com.motiengineering.bidmgmt.dto;

import java.util.List;

public record DealRegistrationSummaryDto(
        int expiringSoonCount,
        int pendingOemCount,
        int approvedCount,
        /** Short "title (OEM)" labels for PREPARING bids with an OEM brand on a lot but no linked deal registration - capped to a handful. */
        List<String> bidsPreparingWithoutRegistration) {
}
