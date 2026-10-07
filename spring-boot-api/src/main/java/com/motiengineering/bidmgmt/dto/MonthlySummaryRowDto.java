package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

/** One division's counts for a single calendar month - identified/submitted/won/lost/dropped, per the brief's monthly report. */
public record MonthlySummaryRowDto(
        String divisionLabel,
        int identified,
        int submitted,
        int won,
        int lost,
        int dropped,
        BigDecimal wonValueEtb,
        BigDecimal wonValueUsd) {
}
