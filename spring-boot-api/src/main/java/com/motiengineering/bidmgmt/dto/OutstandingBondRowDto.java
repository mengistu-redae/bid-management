package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        boolean expiringSoon) {
}
