package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

/** Win rate for one group (a division, an account officer, or an OEM brand), among lots with a decided outcome (WON or LOST). */
public record WinRateRowDto(
        String groupLabel,
        int wonCount,
        int lostCount,
        int winRatePercent,
        BigDecimal wonValueEtb,
        BigDecimal wonValueUsd) {
}
