package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OutstandingBondRowDto(
        UUID lotId,
        UUID bidId,
        String bidTitle,
        String organizationName,
        String lotLabel,
        BigDecimal amount,
        String currency,
        Instant bidClosingAt,
        /** bid opening + validity days, when both are known - an estimate, since bid bond issue date isn't tracked until the phase-3 dedicated entity. */
        LocalDate estimatedExpiry,
        boolean expiringSoon,
        /** 0 or 1 entries - a lot has at most one division. */
        List<String> divisionNames) {
}
