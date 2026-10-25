package com.motiengineering.bidmgmt.scheduler;

import com.motiengineering.bidmgmt.domain.DashboardKpiSnapshot;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.repository.DashboardKpiSnapshotRepository;
import com.motiengineering.bidmgmt.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Captures one dashboard_kpi_snapshot row per calendar day - the portfolio-wide (no division
 * filter) values of the same 6 numbers the Overview tab's stat cards show, so DashboardService
 * can later compare "today" against a real stored day instead of fabricating a trend.
 * Idempotent: re-running on the same day overwrites that day's row rather than duplicating it,
 * so a redeploy or manual trigger can't skew the history.
 */
@Service
@RequiredArgsConstructor
public class DashboardKpiSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(DashboardKpiSnapshotJob.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final DashboardService dashboardService;
    private final DashboardKpiSnapshotRepository dashboardKpiSnapshotRepository;

    @Scheduled(cron = "${bidmgmt.notifications.kpi-snapshot-cron}")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(ZONE);
        DashboardDto dashboard = dashboardService.build(null);

        int behindStageTargetCount = dashboard.delaySummary().amberCount() + dashboard.delaySummary().redCount() + dashboard.delaySummary().criticalCount();
        BigDecimal openPipelineEtb = dashboard.pipelineByDivision().stream().map(r -> r.totalEtb()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openPipelineUsd = dashboard.pipelineByDivision().stream().map(r -> r.totalUsd()).reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardKpiSnapshot snapshot = dashboardKpiSnapshotRepository.findBySnapshotDate(today).orElseGet(DashboardKpiSnapshot::new);
        snapshot.setSnapshotDate(today);
        snapshot.setClosingSoonCount(dashboard.closingThisWeek().size());
        snapshot.setMissingReadinessCount(dashboard.missingReadiness().size());
        snapshot.setBehindStageTargetCount(behindStageTargetCount);
        snapshot.setOpenPipelineEtb(openPipelineEtb);
        snapshot.setOpenPipelineUsd(openPipelineUsd);
        snapshot.setBidBondsOutstandingEtb(dashboard.outstandingBondTotalEtb());
        snapshot.setDealRegsExpiringCount(dashboard.expiringDealRegistrations().size());
        dashboardKpiSnapshotRepository.save(snapshot);

        log.info("Captured dashboard KPI snapshot for {}", today);
    }
}
