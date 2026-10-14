package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DelayReasonCode;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.StageHistoryEntry;
import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
import com.motiengineering.bidmgmt.dto.DelayAnalyticsReportDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.DelayReasonCodeRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the aggregation math: per-stage duration stats (avg/median/p90/%-over-target), breakdowns, delay-reason Pareto, waiting-on totals (including an open episode), and date slippage. */
class DelayAnalyticsServiceTest {

    private BidRepository bidRepository;
    private StageHistoryService stageHistoryService;
    private HoldService holdService;
    private IndicatorService indicatorService;
    private BusinessDayCalendarService businessDayCalendarService;
    private AuditLogRepository auditLogRepository;
    private DelayReasonCodeRepository delayReasonCodeRepository;
    private DelayAnalyticsService service;

    private Bid bid;
    private BidLot lot;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
        DealRegistrationRepository dealRegistrationRepository = mock(DealRegistrationRepository.class);
        LotAccountOfficerRepository lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        delayReasonCodeRepository = mock(DelayReasonCodeRepository.class);
        stageHistoryService = mock(StageHistoryService.class);
        holdService = mock(HoldService.class);
        indicatorService = mock(IndicatorService.class);
        businessDayCalendarService = mock(BusinessDayCalendarService.class);

        when(opportunityRepository.findAll()).thenReturn(List.of());
        when(dealRegistrationRepository.findAll()).thenReturn(List.of());
        when(lotAccountOfficerRepository.findById_LotId(any())).thenReturn(List.of());
        when(holdService.holdWindowsFor(any(), any())).thenReturn(List.of());
        when(delayReasonCodeRepository.findAll()).thenReturn(List.of());

        service = new DelayAnalyticsService(bidRepository, opportunityRepository, dealRegistrationRepository,
                lotAccountOfficerRepository, appUserRepository, auditLogRepository, delayReasonCodeRepository,
                stageHistoryService, holdService, indicatorService, businessDayCalendarService);

        Organization org = new Organization("Test Bank", Sector.BANK);
        bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test Bid");
        bid.setOrganization(org);

        Division division = new Division();
        division.setId(UUID.randomUUID());
        division.setName("Security");

        lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setDivision(division);
        lot.setStatus(BidStatus.PREPARING);

        List<BidLot> lots = new ArrayList<>();
        lots.add(lot);
        bid.setLots(lots);
        when(bidRepository.findAll()).thenReturn(List.of(bid));
        when(auditLogRepository.findAll()).thenReturn(List.of());
    }

    private StageHistoryEntry entry(String stageField, String from, String to, Instant changedAt, String delayReasonCode) {
        return new StageHistoryEntry(EntityType.LOT, lot.getId(), stageField, from, to, null, delayReasonCode, null, changedAt, false);
    }

    @Test
    void computesAverageMedianP90AndPercentOverTargetForACompletedStage() {
        Instant base = Instant.parse("2026-01-05T00:00:00Z"); // a Monday
        List<StageHistoryEntry> history = List.of(
                entry("status", null, "UNDER_REVIEW", base, null),
                entry("status", "UNDER_REVIEW", "PREPARING", base.plus(2, java.time.temporal.ChronoUnit.DAYS), null) // UNDER_REVIEW lasted 2 business days (Mon->Wed)
        );
        when(stageHistoryService.history(EntityType.LOT, lot.getId())).thenReturn(history);
        when(businessDayCalendarService.businessDaysBetween(any(), any(), any())).thenReturn(2L);

        StageTarget target = new StageTarget();
        target.setTargetType(TargetType.DURATION);
        target.setTargetDays(BigDecimal.ONE); // 2 actual > 1 target -> over target
        when(indicatorService.resolveTarget(eq(EntityType.LOT), eq("UNDER_REVIEW"), any())).thenReturn(Optional.of(target));

        DelayAnalyticsReportDto report = service.build(null, null);

        var stat = report.stageDurations().stream().filter(s -> s.stageValue().equals("UNDER_REVIEW")).findFirst().orElseThrow();
        assertThat(stat.completedCount()).isEqualTo(1);
        assertThat(stat.avgBusinessDays()).isEqualTo(2.0);
        assertThat(stat.medianBusinessDays()).isEqualTo(2.0);
        assertThat(stat.p90BusinessDays()).isEqualTo(2.0);
        assertThat(stat.percentOverTarget()).isEqualTo(100.0);

        var divisionRow = report.byDivision().stream().filter(r -> r.key().equals("Security")).findFirst().orElseThrow();
        assertThat(divisionRow.completedCount()).isEqualTo(1);
        assertThat(divisionRow.totalDelayDays()).isEqualTo(1.0); // 2 actual - 1 target
    }

    @Test
    void aggregatesDelayReasonCodesByExcessDaysOverTarget() {
        Instant base = Instant.parse("2026-01-05T00:00:00Z");
        List<StageHistoryEntry> history = List.of(
                entry("status", null, "UNDER_REVIEW", base, null),
                entry("status", "UNDER_REVIEW", "PREPARING", base.plus(5, java.time.temporal.ChronoUnit.DAYS), "WAITING_OEM_MAF")
        );
        when(stageHistoryService.history(EntityType.LOT, lot.getId())).thenReturn(history);
        when(businessDayCalendarService.businessDaysBetween(any(), any(), any())).thenReturn(5L);

        StageTarget target = new StageTarget();
        target.setTargetType(TargetType.DURATION);
        target.setTargetDays(BigDecimal.valueOf(2));
        when(indicatorService.resolveTarget(any(), any(), any())).thenReturn(Optional.of(target));
        when(delayReasonCodeRepository.findAll()).thenReturn(List.of(reasonCode("WAITING_OEM_MAF", "Waiting OEM MAF")));

        DelayAnalyticsReportDto report = service.build(null, null);

        var row = report.delayReasonPareto().stream().filter(r -> r.code().equals("WAITING_OEM_MAF")).findFirst().orElseThrow();
        assertThat(row.label()).isEqualTo("Waiting OEM MAF");
        assertThat(row.occurrences()).isEqualTo(1);
        assertThat(row.totalDelayDays()).isEqualTo(3.0); // 5 actual - 2 target
    }

    @Test
    void waitingOnTotalsIncludeTheStillOpenEpisodeThroughNow() {
        Instant base = Instant.parse("2026-01-05T00:00:00Z");
        List<StageHistoryEntry> history = List.of(
                entry("waitingOn", null, "OEM", base, null) // never closed - still waiting
        );
        when(stageHistoryService.history(EntityType.LOT, lot.getId())).thenReturn(history);
        when(businessDayCalendarService.businessDaysBetween(any(), any(), any())).thenReturn(4L);

        DelayAnalyticsReportDto report = service.build(null, Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate());

        var row = report.waitingOnTotals().stream().filter(r -> r.partyType().equals("OEM")).findFirst().orElseThrow();
        assertThat(row.episodeCount()).isEqualTo(1);
        assertThat(row.totalDaysWaited()).isEqualTo(4.0);
    }

    @Test
    void dateSlippageReportComputesOriginalVsCurrentAndChangeCount() {
        AuditLogEntry first = auditEntry("BID", bid.getId(), "closingAt", "2026-01-01T00:00:00Z", "2026-01-10T00:00:00Z", Instant.parse("2026-01-02T00:00:00Z"));
        AuditLogEntry second = auditEntry("BID", bid.getId(), "closingAt", "2026-01-10T00:00:00Z", "2026-01-20T00:00:00Z", Instant.parse("2026-01-12T00:00:00Z"));
        when(auditLogRepository.findAll()).thenReturn(List.of(first, second));
        when(stageHistoryService.history(any(), any())).thenReturn(List.of());

        DelayAnalyticsReportDto report = service.build(null, null);

        var row = report.topDateSlippage().stream().filter(r -> r.entityType().equals("BID")).findFirst().orElseThrow();
        assertThat(row.changeCount()).isEqualTo(2);
        assertThat(row.slippageDays()).isEqualTo(19L); // 2026-01-01 -> 2026-01-20
        assertThat(row.label()).isEqualTo("Test Bid");
    }

    private DelayReasonCode reasonCode(String code, String label) {
        DelayReasonCode entry = new DelayReasonCode();
        entry.setCode(code);
        entry.setLabel(label);
        return entry;
    }

    private AuditLogEntry auditEntry(String entityType, UUID entityId, String fieldName, String oldValue, String newValue, Instant changedAt) {
        AuditLogEntry entry = new AuditLogEntry(entityType, entityId, fieldName, oldValue, newValue, null);
        try {
            var field = AuditLogEntry.class.getDeclaredField("changedAt");
            field.setAccessible(true);
            field.set(entry, changedAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return entry;
    }
}
