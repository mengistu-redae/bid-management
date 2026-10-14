package com.motiengineering.bidmgmt.dto;

import java.time.LocalDate;
import java.util.List;

public record DelayAnalyticsReportDto(
        LocalDate from,
        LocalDate to,
        List<StageDurationStatDto> stageDurations,
        List<MonthlyStageTrendDto> monthlyTrend,
        List<DelayBreakdownRowDto> byDivision,
        List<DelayBreakdownRowDto> byOfficer,
        List<DelayBreakdownRowDto> byCustomer,
        List<DelayBreakdownRowDto> byOem,
        List<DelayReasonParetoRowDto> delayReasonPareto,
        List<DateSlippageRowDto> topDateSlippage,
        List<FieldSlippageSummaryDto> slippageByField,
        List<WaitingOnTotalDto> waitingOnTotals) {
}
