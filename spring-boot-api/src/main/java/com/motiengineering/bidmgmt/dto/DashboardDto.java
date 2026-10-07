package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
        List<ClosingSoonRowDto> closingThisWeek,
        List<ClosingSoonRowDto> closingNextWeek,
        List<ClarificationRowDto> upcomingClarifications,
        List<PipelineValueRowDto> pipelineByDivision,
        List<PipelineValueRowDto> pipelineByStatus,
        List<WinRateRowDto> winRateByDivision,
        List<WinRateRowDto> winRateByOfficer,
        List<WinRateRowDto> winRateByOem,
        List<OutstandingBondRowDto> outstandingBonds,
        BigDecimal outstandingBondTotalEtb,
        BigDecimal outstandingBondTotalUsd) {
}
