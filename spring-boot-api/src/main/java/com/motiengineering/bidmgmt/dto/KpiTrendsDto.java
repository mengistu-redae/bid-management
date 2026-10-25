package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Previous-period values for the Overview tab's 6 KPI cards, from the nearest
 * {@code dashboard_kpi_snapshot} row at least ~5 days old - never the live DashboardDto
 * fields recomputed, so the comparison is always against a real stored day, not today.
 * Every field is null (and previousSnapshotDate is null) when no snapshot old enough exists
 * yet - the frontend shows no trend line rather than a misleading one.
 */
public record KpiTrendsDto(
        LocalDate previousSnapshotDate,
        Integer closingSoonCountPrevious,
        Integer missingReadinessCountPrevious,
        Integer behindStageTargetCountPrevious,
        BigDecimal openPipelineEtbPrevious,
        BigDecimal openPipelineUsdPrevious,
        BigDecimal bidBondsOutstandingEtbPrevious,
        Integer dealRegsExpiringCountPrevious) {

    public static final KpiTrendsDto EMPTY = new KpiTrendsDto(null, null, null, null, null, null, null, null);
}
