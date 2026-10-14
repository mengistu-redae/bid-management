package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.StageHistoryEntry;
import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
import com.motiengineering.bidmgmt.dto.DateSlippageRowDto;
import com.motiengineering.bidmgmt.dto.DelayAnalyticsReportDto;
import com.motiengineering.bidmgmt.dto.DelayBreakdownRowDto;
import com.motiengineering.bidmgmt.dto.DelayReasonParetoRowDto;
import com.motiengineering.bidmgmt.dto.FieldSlippageSummaryDto;
import com.motiengineering.bidmgmt.dto.MonthlyStageTrendDto;
import com.motiengineering.bidmgmt.dto.StageDurationStatDto;
import com.motiengineering.bidmgmt.dto.WaitingOnTotalDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.DelayReasonCodeRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The bottleneck reports: per-stage duration percentiles, breakdowns,
 * delay-reason Pareto, key-date slippage, and time-waiting-by-party -
 * built from the stage_history/audit_log data phases 1-3 already
 * accumulate, not a new tracking mechanism. Stage-duration/breakdown/
 * delay-reason/waiting-on analysis is scoped to BidLot (same boundary as
 * every other phase of this initiative); the date-slippage report covers
 * Bid, Opportunity and DealRegistration, since all three already have
 * audited key-date fields from phase 1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelayAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final int TOP_SLIPPAGE_LIMIT = 15;
    private static final Map<String, List<String>> KEY_DATE_FIELDS = Map.of(
            "BID", List.of("closingAt", "openingAt", "clarificationDeadline"),
            "OPPORTUNITY", List.of("expectedTenderDate"),
            "DEAL_REGISTRATION", List.of("submittedDate", "approvalDate", "expiryDate"));

    private final BidRepository bidRepository;
    private final OpportunityRepository opportunityRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final AppUserRepository appUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final DelayReasonCodeRepository delayReasonCodeRepository;
    private final StageHistoryService stageHistoryService;
    private final HoldService holdService;
    private final IndicatorService indicatorService;
    private final BusinessDayCalendarService businessDayCalendarService;

    private record Acc(List<Double> durations, double[] totalDelayDays) {
        static Acc empty() {
            return new Acc(new ArrayList<>(), new double[1]);
        }
    }

    public DelayAnalyticsReportDto build(LocalDate from, LocalDate to) {
        Instant fromInstant = from == null ? Instant.EPOCH : from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to == null ? Instant.now() : to.plusDays(1).atStartOfDay(ZONE).toInstant();

        Map<String, List<Double>> durationsByStage = new LinkedHashMap<>();
        Map<String, Long> overTargetCountByStage = new LinkedHashMap<>();
        Map<String, Long> comparableCountByStage = new LinkedHashMap<>();
        Map<String, List<Double>> monthlyDurations = new LinkedHashMap<>();
        Map<String, Acc> byDivision = new LinkedHashMap<>();
        Map<String, Acc> byOfficer = new LinkedHashMap<>();
        Map<String, Acc> byCustomer = new LinkedHashMap<>();
        Map<String, Acc> byOem = new LinkedHashMap<>();
        Map<String, double[]> delayDaysByReasonCode = new LinkedHashMap<>();
        Map<String, double[]> waitingOnAgg = new LinkedHashMap<>();

        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                accumulateLot(bid, lot, fromInstant, toInstant, durationsByStage, overTargetCountByStage,
                        comparableCountByStage, monthlyDurations, byDivision, byOfficer, byCustomer, byOem,
                        delayDaysByReasonCode, waitingOnAgg);
            }
        }

        List<StageDurationStatDto> stageDurations = durationsByStage.entrySet().stream()
                .map(e -> toStat(e.getKey(), e.getValue(), overTargetCountByStage.getOrDefault(e.getKey(), 0L), comparableCountByStage.getOrDefault(e.getKey(), 0L)))
                .sorted(Comparator.comparing(StageDurationStatDto::stageValue))
                .toList();

        List<MonthlyStageTrendDto> monthlyTrend = monthlyDurations.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    return new MonthlyStageTrendDto(parts[0], parts[1], e.getValue().size(), average(e.getValue()));
                })
                .sorted(Comparator.comparing(MonthlyStageTrendDto::stageValue).thenComparing(MonthlyStageTrendDto::yearMonth))
                .toList();

        Map<String, String> reasonLabels = delayReasonCodeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(com.motiengineering.bidmgmt.domain.DelayReasonCode::getCode, com.motiengineering.bidmgmt.domain.DelayReasonCode::getLabel, (a, b) -> a));
        List<DelayReasonParetoRowDto> pareto = delayDaysByReasonCode.entrySet().stream()
                .map(e -> new DelayReasonParetoRowDto(e.getKey(), reasonLabels.getOrDefault(e.getKey(), e.getKey()), (long) e.getValue()[0], round1(e.getValue()[1])))
                .sorted(Comparator.comparingDouble(DelayReasonParetoRowDto::totalDelayDays).reversed())
                .toList();

        List<WaitingOnTotalDto> waitingOnTotals = waitingOnAgg.entrySet().stream()
                .map(e -> new WaitingOnTotalDto(e.getKey(), (long) e.getValue()[0], round1(e.getValue()[1])))
                .sorted(Comparator.comparingDouble(WaitingOnTotalDto::totalDaysWaited).reversed())
                .toList();

        var slippage = buildDateSlippage(fromInstant, toInstant);

        return new DelayAnalyticsReportDto(from, to, stageDurations, monthlyTrend,
                toBreakdownList("division", byDivision), toBreakdownList("officer", byOfficer),
                toBreakdownList("customer", byCustomer), toBreakdownList("oem", byOem),
                pareto, slippage.topRows(), slippage.byField(), waitingOnTotals);
    }

    private void accumulateLot(Bid bid, BidLot lot, Instant fromInstant, Instant toInstant,
                                Map<String, List<Double>> durationsByStage, Map<String, Long> overTargetCountByStage,
                                Map<String, Long> comparableCountByStage, Map<String, List<Double>> monthlyDurations,
                                Map<String, Acc> byDivision, Map<String, Acc> byOfficer, Map<String, Acc> byCustomer, Map<String, Acc> byOem,
                                Map<String, double[]> delayDaysByReasonCode, Map<String, double[]> waitingOnAgg) {
        List<StageHistoryEntry> history = stageHistoryService.history(EntityType.LOT, lot.getId());
        if (history.isEmpty()) {
            return;
        }
        List<Instant[]> holdWindows = holdService.holdWindowsFor(EntityType.LOT, lot.getId());
        TargetContext context = new TargetContext(
                lot.getDivision() == null ? null : lot.getDivision().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getSector(),
                lot.getOemBrand(), null);

        List<StageHistoryEntry> statusHistory = history.stream()
                .filter(h -> "status".equals(h.getStageField()))
                .sorted(Comparator.comparing(StageHistoryEntry::getChangedAt))
                .toList();
        List<String> officerNames = officerNames(lot);

        for (int i = 0; i < statusHistory.size() - 1; i++) {
            StageHistoryEntry entry = statusHistory.get(i);
            StageHistoryEntry exit = statusHistory.get(i + 1);
            if (entry.getChangedAt().isBefore(fromInstant) || entry.getChangedAt().isAfter(toInstant)) {
                continue;
            }
            String stageValue = entry.getToValue();
            if (stageValue == null) {
                continue;
            }
            double businessDays = businessDayCalendarService.businessDaysBetween(entry.getChangedAt(), exit.getChangedAt(), holdWindows);

            durationsByStage.computeIfAbsent(stageValue, k -> new ArrayList<>()).add(businessDays);
            String yearMonth = YearMonth.from(entry.getChangedAt().atZone(ZONE)).toString();
            monthlyDurations.computeIfAbsent(stageValue + "|" + yearMonth, k -> new ArrayList<>()).add(businessDays);

            Optional<StageTarget> target = indicatorService.resolveTarget(EntityType.LOT, stageValue, context);
            double excessDays = 0;
            if (target.isPresent() && target.get().getTargetType() == TargetType.DURATION && target.get().getTargetDays() != null) {
                comparableCountByStage.merge(stageValue, 1L, Long::sum);
                double targetDays = target.get().getTargetDays().doubleValue();
                if (businessDays > targetDays) {
                    overTargetCountByStage.merge(stageValue, 1L, Long::sum);
                    excessDays = businessDays - targetDays;
                }
            }

            accumulate(byDivision, lot.getDivision() == null ? "Unassigned" : lot.getDivision().getName(), businessDays, excessDays);
            for (String officerName : officerNames) {
                accumulate(byOfficer, officerName, businessDays, excessDays);
            }
            accumulate(byCustomer, bid.getOrganization() == null ? "Unknown" : bid.getOrganization().getName(), businessDays, excessDays);
            if (lot.getOemBrand() != null && !lot.getOemBrand().isBlank()) {
                accumulate(byOem, lot.getOemBrand(), businessDays, excessDays);
            }

            if (exit.getDelayReasonCode() != null) {
                double[] agg = delayDaysByReasonCode.computeIfAbsent(exit.getDelayReasonCode(), k -> new double[2]);
                agg[0]++;
                agg[1] += excessDays;
            }
        }

        List<StageHistoryEntry> waitingHistory = history.stream()
                .filter(h -> "waitingOn".equals(h.getStageField()))
                .sorted(Comparator.comparing(StageHistoryEntry::getChangedAt))
                .toList();
        for (int i = 0; i < waitingHistory.size(); i++) {
            StageHistoryEntry entry = waitingHistory.get(i);
            if (entry.getToValue() == null || entry.getChangedAt().isAfter(toInstant)) {
                continue;
            }
            Instant segmentEnd = i + 1 < waitingHistory.size() ? waitingHistory.get(i + 1).getChangedAt() : Instant.now();
            Instant clampedEnd = segmentEnd.isAfter(toInstant) ? toInstant : segmentEnd;
            if (!clampedEnd.isAfter(entry.getChangedAt())) {
                continue;
            }
            double days = businessDayCalendarService.businessDaysBetween(entry.getChangedAt(), clampedEnd, holdWindows);
            double[] agg = waitingOnAgg.computeIfAbsent(entry.getToValue(), k -> new double[2]);
            agg[0]++;
            agg[1] += days;
        }
    }

    private record SlippageResult(List<DateSlippageRowDto> topRows, List<FieldSlippageSummaryDto> byField) {
    }

    private SlippageResult buildDateSlippage(Instant fromInstant, Instant toInstant) {
        Map<UUID, String> bidTitles = bidRepository.findAll().stream().collect(java.util.stream.Collectors.toMap(Bid::getId, Bid::getTitle, (a, b) -> a));
        Map<UUID, String> opportunityTitles = opportunityRepository.findAll().stream().collect(java.util.stream.Collectors.toMap(Opportunity::getId, Opportunity::getTitle, (a, b) -> a));
        Map<UUID, String> dealRegLabels = dealRegistrationRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(DealRegistration::getId, r -> r.getOem().getName() + " - " + r.getOrganization().getName(), (a, b) -> a));

        Map<String, List<AuditLogEntry>> grouped = new LinkedHashMap<>();
        for (AuditLogEntry entry : auditLogRepository.findAll()) {
            List<String> fields = KEY_DATE_FIELDS.get(entry.getEntityType());
            if (fields == null || !fields.contains(entry.getFieldName())) {
                continue;
            }
            if (entry.getChangedAt().isBefore(fromInstant) || entry.getChangedAt().isAfter(toInstant)) {
                continue;
            }
            String key = entry.getEntityType() + "|" + entry.getEntityId() + "|" + entry.getFieldName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        List<DateSlippageRowDto> rows = new ArrayList<>();
        Map<String, double[]> byFieldAgg = new LinkedHashMap<>();
        for (Map.Entry<String, List<AuditLogEntry>> group : grouped.entrySet()) {
            String[] parts = group.getKey().split("\\|", 3);
            String entityType = parts[0];
            UUID entityId = UUID.fromString(parts[1]);
            String fieldName = parts[2];
            List<AuditLogEntry> entries = group.getValue().stream().sorted(Comparator.comparing(AuditLogEntry::getChangedAt)).toList();
            String original = entries.get(0).getOldValue();
            String current = entries.get(entries.size() - 1).getNewValue();
            Long slippage = KeyDateHistoryService.slippageDays(original, current);
            String label = switch (entityType) {
                case "BID" -> bidTitles.getOrDefault(entityId, entityId.toString());
                case "OPPORTUNITY" -> opportunityTitles.getOrDefault(entityId, entityId.toString());
                case "DEAL_REGISTRATION" -> dealRegLabels.getOrDefault(entityId, entityId.toString());
                default -> entityId.toString();
            };
            rows.add(new DateSlippageRowDto(entityType, entityId, label, fieldName, entries.size(), slippage));

            double[] agg = byFieldAgg.computeIfAbsent(entityType + "." + fieldName, k -> new double[2]);
            agg[0] += entries.size();
            agg[1] += slippage == null ? 0 : slippage;
        }

        List<DateSlippageRowDto> top = rows.stream()
                .sorted(Comparator.comparingLong(DateSlippageRowDto::changeCount).reversed())
                .limit(TOP_SLIPPAGE_LIMIT)
                .toList();
        List<FieldSlippageSummaryDto> byField = byFieldAgg.entrySet().stream()
                .map(e -> {
                    String[] p = e.getKey().split("\\.", 2);
                    return new FieldSlippageSummaryDto(p[0], p[1], (long) e.getValue()[0], round1(e.getValue()[1]));
                })
                .sorted(Comparator.comparingDouble(FieldSlippageSummaryDto::totalSlippageDays).reversed())
                .toList();
        return new SlippageResult(top, byField);
    }

    private void accumulate(Map<String, Acc> map, String key, double durationDays, double excessDays) {
        Acc acc = map.computeIfAbsent(key, k -> Acc.empty());
        acc.durations().add(durationDays);
        acc.totalDelayDays()[0] += excessDays;
    }

    private List<DelayBreakdownRowDto> toBreakdownList(String dimension, Map<String, Acc> map) {
        return map.entrySet().stream()
                .map(e -> new DelayBreakdownRowDto(dimension, e.getKey(), e.getValue().durations().size(), round1(average(e.getValue().durations())), round1(e.getValue().totalDelayDays()[0])))
                .sorted(Comparator.comparingDouble(DelayBreakdownRowDto::totalDelayDays).reversed())
                .toList();
    }

    private StageDurationStatDto toStat(String stageValue, List<Double> durations, long overTargetCount, long comparableCount) {
        List<Double> sorted = durations.stream().sorted().toList();
        double percentOverTarget = comparableCount == 0 ? 0 : (overTargetCount * 100.0) / comparableCount;
        return new StageDurationStatDto(stageValue, sorted.size(), round1(average(sorted)), round1(percentile(sorted, 50)), round1(percentile(sorted, 90)), round1(percentOverTarget));
    }

    private double average(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double percentile(List<Double> sortedValues, int pct) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(pct / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(sortedValues.size() - 1, index));
        return sortedValues.get(index);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private List<String> officerNames(BidLot lot) {
        return lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                .map((LotAccountOfficer loa) -> appUserRepository.findById(loa.getId().getUserId()).map(u -> u.getFullName()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
