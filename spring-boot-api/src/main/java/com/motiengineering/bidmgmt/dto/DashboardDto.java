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
        BigDecimal outstandingBondTotalUsd,
        List<ExpiringDealRegistrationRowDto> expiringDealRegistrations,
        DelaySummaryDto delaySummary,
        /** Every currently open bid missing its MAF and/or vendor quote, regardless of closing date - a superset of closingThisWeek's redFlag rows. */
        List<ClosingSoonRowDto> missingReadiness,
        List<LifecycleStageRowDto> lifecycleStages,
        List<OpenNoResultRowDto> bidsPastOpeningNoResult,
        DealRegistrationSummaryDto dealRegistrationSummary,
        List<DivisionPillDto> divisionFilters,
        /** Open bids where no lot has an estimated value recorded yet. */
        int unpricedBidCount,
        int bondsExpiringWithin90DaysCount) {
}
