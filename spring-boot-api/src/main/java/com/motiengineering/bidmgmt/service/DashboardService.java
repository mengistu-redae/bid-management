package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.enums.BidBondForm;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
import com.motiengineering.bidmgmt.dto.ClarificationRowDto;
import com.motiengineering.bidmgmt.dto.ClosingSoonRowDto;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.dto.DealRegistrationSummaryDto;
import com.motiengineering.bidmgmt.dto.DelaySummaryDto;
import com.motiengineering.bidmgmt.dto.DivisionPillDto;
import com.motiengineering.bidmgmt.dto.ExpiringDealRegistrationRowDto;
import com.motiengineering.bidmgmt.dto.LifecycleStageRowDto;
import com.motiengineering.bidmgmt.dto.OpenNoResultRowDto;
import com.motiengineering.bidmgmt.dto.OutstandingBondRowDto;
import com.motiengineering.bidmgmt.dto.PipelineValueRowDto;
import com.motiengineering.bidmgmt.dto.WinRateRowDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
import com.motiengineering.bidmgmt.util.BidRiskEvaluator;
import com.motiengineering.bidmgmt.util.DivisionNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All-in-memory aggregation over every bid - a deliberate simplification at
 * this scale (a sales team of 10-20 people, a few hundred bids a year): a
 * handful of SQL GROUP BYs would be faster at real scale, but would also be
 * four or five bespoke queries to maintain for a report that's read once a
 * day. Revisit if the bid count ever makes this slow.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final int BOND_EXPIRING_SOON_DAYS = 14;
    private static final int BOND_EXPIRING_WITHIN_DAYS = 90;

    private static final int DEAL_REGISTRATION_EXPIRING_SOON_DAYS = 30;
    private static final int DEAL_REG_MISSING_LIST_LIMIT = 5;

    private final BidRepository bidRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final AppUserRepository appUserRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final DivisionRepository divisionRepository;
    private final LotScopeTypeRepository lotScopeTypeRepository;
    private final ChecklistService checklistService;
    private final DelaysBoardService delaysBoardService;

    private static final int TOP_DELAYED_LIMIT = 10;

    public DashboardDto build() {
        return build(null);
    }

    /**
     * divisionFilter is null/blank for "all divisions", the literal "none"
     * for the no-division bucket, or a division's UUID string - see the
     * division filter pills on the dashboard. The pills themselves are
     * always computed from the unfiltered bid set so their counts stay
     * stable reference numbers as the user clicks between them.
     */
    public DashboardDto build(String divisionFilter) {
        List<Bid> allBids = bidRepository.findAll();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);

        List<DivisionPillDto> divisionFilters = divisionFilters(allBids, now);
        List<Bid> scoped = applyDivisionFilter(allBids, divisionFilter);

        List<ExpiringDealRegistrationRowDto> expiringRegs = expiringDealRegistrations(today);

        return new DashboardDto(
                closingWithin(scoped, now, 0, 7),
                closingWithin(scoped, now, 7, 14),
                upcomingClarifications(scoped, now),
                pipelineByDivision(scoped),
                pipelineByStatus(scoped),
                winRateByDivision(scoped),
                winRateByOfficer(scoped),
                winRateByOem(scoped),
                outstandingBonds(scoped, today),
                sumBondsByCurrency(scoped, Currency.ETB),
                sumBondsByCurrency(scoped, Currency.USD),
                expiringRegs,
                delaySummary(divisionFilter),
                missingReadiness(scoped),
                lifecycleStageSummary(scoped),
                bidsPastOpeningNoResult(scoped, now),
                dealRegistrationSummary(scoped, expiringRegs),
                divisionFilters,
                unpricedBidCount(scoped),
                bondsExpiringWithinDaysCount(scoped, today, BOND_EXPIRING_WITHIN_DAYS));
    }

    /** Same delay summary the dashboard embeds, exposed standalone for the weekly bottleneck email. */
    public DelaySummaryDto delaySummary() {
        return delaySummary(null);
    }

    /**
     * Green/amber/red/critical/stale counts plus the worst-offending open lots, contracts and
     * shipments - delegates to DelaysBoardService so this and the real /delays board can never
     * disagree. divisionFilter is null/blank for "all divisions", the literal "none" for the
     * no-division bucket, or a division's UUID string, same as build(String).
     */
    private DelaySummaryDto delaySummary(String divisionFilter) {
        UUID divisionId = null;
        boolean noDivisionOnly = false;
        if (divisionFilter != null && !divisionFilter.isBlank()) {
            if ("none".equals(divisionFilter)) {
                noDivisionOnly = true;
            } else {
                divisionId = UUID.fromString(divisionFilter);
            }
        }
        return delaysBoardService.summary(divisionId, noDivisionOnly, TOP_DELAYED_LIMIT);
    }

    private List<ExpiringDealRegistrationRowDto> expiringDealRegistrations(LocalDate today) {
        LocalDate cutoff = today.plusDays(DEAL_REGISTRATION_EXPIRING_SOON_DAYS);
        List<ExpiringDealRegistrationRowDto> rows = new ArrayList<>();
        for (DealRegistration reg : dealRegistrationRepository.findAll()) {
            if (reg.getExpiryDate() == null || reg.getStatus() == DealRegistrationStatus.EXPIRED || reg.getStatus() == DealRegistrationStatus.REJECTED) {
                continue;
            }
            if (!reg.getExpiryDate().isBefore(today) && reg.getExpiryDate().isBefore(cutoff)) {
                rows.add(new ExpiringDealRegistrationRowDto(reg.getId(), reg.getOem().getName(), reg.getOrganization().getName(), reg.getExpiryDate()));
            }
        }
        rows.sort((a, b) -> a.expiryDate().compareTo(b.expiryDate()));
        return rows;
    }

    private List<ClosingSoonRowDto> closingWithin(List<Bid> bids, Instant now, int fromDays, int toDays) {
        Instant from = now.plus(fromDays, ChronoUnit.DAYS);
        Instant to = now.plus(toDays, ChronoUnit.DAYS);
        List<ClosingSoonRowDto> rows = new ArrayList<>();
        for (Bid bid : bids) {
            if (bid.getClosingAt() == null || bid.getClosingAt().isBefore(from) || bid.getClosingAt().isAfter(to)) {
                continue;
            }
            if (bid.getStatus().isTerminal()) {
                continue;
            }
            rows.add(closingSoonRow(bid, now));
        }
        rows.sort((a, b) -> a.closingAt().compareTo(b.closingAt()));
        return rows;
    }

    private ClosingSoonRowDto closingSoonRow(Bid bid, Instant now) {
        int progress = averageChecklistProgress(bid);
        String missing = BidRiskEvaluator.redFlagReason(bid, now);
        BigDecimal[] bondTotals = bidBondTotals(bid);
        return new ClosingSoonRowDto(
                bid.getId(), bid.getTitle(), bid.getOrganization().getName(), bid.getClosingAt(),
                bid.getStatus().name(), progress, missing != null, missing, DivisionNames.distinct(bid),
                readinessGaps(bid), ownerLabel(bid), bondTotals[0], bondTotals[1]);
    }

    private int averageChecklistProgress(Bid bid) {
        if (bid.getLots().isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (BidLot lot : bid.getLots()) {
            sum += checklistService.progressPercent(lot.getId());
        }
        return sum / bid.getLots().size();
    }

    /**
     * Division/Owner/Go-no-go/MAF/Vendor quote/Closing time/Verify bond/Scope
     * - whichever of these the bid is still missing. A gap fires if ANY lot
     * is missing it (conservative - catches a partial miss on a multi-lot
     * bid). "Closing time" is a heuristic: a closingAt whose local
     * time-of-day is exactly midnight is treated as "date known, time still
     * TBC", since there's no dedicated flag for that in the data model.
     */
    private List<String> readinessGaps(Bid bid) {
        List<String> gaps = new ArrayList<>();
        if (bid.getLots().stream().anyMatch(l -> l.getDivision() == null)) {
            gaps.add("Division");
        }
        if (bid.getLots().stream().anyMatch(l -> lotAccountOfficerRepository.findById_LotId(l.getId()).isEmpty())) {
            gaps.add("Owner");
        }
        if (bid.getGoNoGoDecision() == null) {
            gaps.add("Go / no-go");
        }
        if (BidRiskEvaluator.missingMaf(bid)) {
            gaps.add("MAF");
        }
        if (BidRiskEvaluator.missingVendorQuote(bid)) {
            gaps.add("Vendor quote");
        }
        if (bid.getClosingAt() != null && bid.getClosingAt().atZone(ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            gaps.add("Closing time");
        }
        if (bid.getLots().stream().anyMatch(l -> l.getBidBondAmount() != null && l.getBidBondForm() == BidBondForm.UNKNOWN)) {
            gaps.add("Verify bond");
        }
        if (bid.getLots().stream().anyMatch(l -> lotScopeTypeRepository.findById_LotId(l.getId()).isEmpty())) {
            gaps.add("Scope");
        }
        return gaps;
    }

    private String ownerLabel(Bid bid) {
        List<String> names = new ArrayList<>();
        for (BidLot lot : bid.getLots()) {
            for (LotAccountOfficer officer : lotAccountOfficerRepository.findById_LotId(lot.getId())) {
                String name = officerName(officer.getId().getUserId());
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** [etb, usd] - outstanding (not returned) bid bond amounts summed across the bid's lots. */
    private BigDecimal[] bidBondTotals(Bid bid) {
        BigDecimal etb = BigDecimal.ZERO;
        BigDecimal usd = BigDecimal.ZERO;
        for (BidLot lot : bid.getLots()) {
            if (lot.getBidBondAmount() == null || lot.getBidBondCurrency() == null || lot.isBidBondReturned()) {
                continue;
            }
            if (lot.getBidBondCurrency() == Currency.ETB) {
                etb = etb.add(lot.getBidBondAmount());
            } else {
                usd = usd.add(lot.getBidBondAmount());
            }
        }
        return new BigDecimal[]{etb, usd};
    }

    /** Every open bid still missing its MAF and/or vendor quote, regardless of closing date - the dashboard's system-wide "Missing MAF or quote" stat. */
    private List<ClosingSoonRowDto> missingReadiness(List<Bid> bids) {
        Instant now = Instant.now();
        List<ClosingSoonRowDto> rows = new ArrayList<>();
        for (Bid bid : bids) {
            if (bid.getStatus().isTerminal()) {
                continue;
            }
            if (!BidRiskEvaluator.missingMaf(bid) && !BidRiskEvaluator.missingVendorQuote(bid)) {
                continue;
            }
            rows.add(closingSoonRow(bid, now));
        }
        rows.sort((a, b) -> {
            if (a.closingAt() == null) return 1;
            if (b.closingAt() == null) return -1;
            return a.closingAt().compareTo(b.closingAt());
        });
        return rows;
    }

    private static String lifecycleBucket(BidStatus status) {
        return switch (status) {
            case IDENTIFIED, UNDER_REVIEW -> "Identified";
            case PREPARING -> "Preparing";
            case SUBMITTED, OPENED -> "Submitted";
            case UNDER_EVALUATION -> "Evaluation";
            case WON -> "Won";
            default -> null; // LOST/DROPPED/CANCELLED excluded - a live-pipeline snapshot, not a historical-outcomes view
        };
    }

    private List<LifecycleStageRowDto> lifecycleStageSummary(List<Bid> bids) {
        List<String> labels = List.of("Identified", "Preparing", "Submitted", "Evaluation", "Won");
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> etbTotals = new LinkedHashMap<>();
        for (String label : labels) {
            counts.put(label, 0);
            etbTotals.put(label, BigDecimal.ZERO);
        }
        for (Bid bid : bids) {
            String bucket = lifecycleBucket(bid.getStatus());
            if (bucket == null) {
                continue;
            }
            counts.merge(bucket, 1, Integer::sum);
            BigDecimal bidEtb = BigDecimal.ZERO;
            for (BidLot lot : bid.getLots()) {
                if (lot.getEstimatedValue() != null && lot.getEstimatedValueCurrency() == Currency.ETB) {
                    bidEtb = bidEtb.add(lot.getEstimatedValue());
                }
            }
            etbTotals.merge(bucket, bidEtb, BigDecimal::add);
        }
        List<LifecycleStageRowDto> rows = new ArrayList<>();
        for (String label : labels) {
            rows.add(new LifecycleStageRowDto(label, counts.get(label), etbTotals.get(label)));
        }
        return rows;
    }

    /** Bids at Opened or Under Evaluation whose opening date has already passed with no lot outcome recorded yet - flags evaluation-stage bids going stale. */
    private List<OpenNoResultRowDto> bidsPastOpeningNoResult(List<Bid> bids, Instant now) {
        List<OpenNoResultRowDto> rows = new ArrayList<>();
        for (Bid bid : bids) {
            if (bid.getStatus() != BidStatus.OPENED && bid.getStatus() != BidStatus.UNDER_EVALUATION) {
                continue;
            }
            Instant reference = bid.getOpeningAt() != null ? bid.getOpeningAt() : bid.getClosingAt();
            if (reference == null || !reference.isBefore(now)) {
                continue;
            }
            boolean anyDecided = bid.getLots().stream().anyMatch(l -> l.getOutcome() != null);
            if (anyDecided) {
                continue;
            }
            rows.add(new OpenNoResultRowDto(bid.getId(), bid.getTitle(), reference));
        }
        rows.sort((a, b) -> a.openingAt().compareTo(b.openingAt()));
        return rows;
    }

    private DealRegistrationSummaryDto dealRegistrationSummary(List<Bid> scopedBids, List<ExpiringDealRegistrationRowDto> expiringRegs) {
        List<DealRegistration> allRegs = dealRegistrationRepository.findAll();
        int pendingOem = 0;
        int approved = 0;
        for (DealRegistration reg : allRegs) {
            if (reg.getStatus() == DealRegistrationStatus.SUBMITTED) {
                pendingOem++;
            } else if (reg.getStatus() == DealRegistrationStatus.APPROVED) {
                approved++;
            }
        }

        List<String> withoutRegistration = new ArrayList<>();
        for (Bid bid : scopedBids) {
            if (withoutRegistration.size() >= DEAL_REG_MISSING_LIST_LIMIT) {
                break;
            }
            if (bid.getStatus() != BidStatus.PREPARING) {
                continue;
            }
            String oem = bid.getLots().stream().map(BidLot::getOemBrand).filter(o -> o != null && !o.isBlank()).findFirst().orElse(null);
            if (oem == null) {
                continue;
            }
            boolean hasRegistration = allRegs.stream().anyMatch(r -> r.getBid() != null && r.getBid().getId().equals(bid.getId()));
            if (!hasRegistration) {
                withoutRegistration.add(bid.getTitle() + " (" + oem + ")");
            }
        }
        return new DealRegistrationSummaryDto(expiringRegs.size(), pendingOem, approved, withoutRegistration);
    }

    /**
     * Division filter pills shown above the dashboard - always computed
     * from the FULL, unfiltered bid set (even when a division filter is
     * currently applied) so the pill counts stay stable reference numbers
     * rather than changing as the user clicks between them. Counts are
     * "bids closing in the next 7 days in this division", matching what
     * the pills sit directly above.
     */
    private List<DivisionPillDto> divisionFilters(List<Bid> allBids, Instant now) {
        List<ClosingSoonRowDto> closingSoon = closingWithin(allBids, now, 0, 7);
        Map<String, Integer> countByDivisionName = new LinkedHashMap<>();
        int noDivisionCount = 0;
        for (ClosingSoonRowDto row : closingSoon) {
            if (row.divisionNames().isEmpty()) {
                noDivisionCount++;
            } else {
                for (String name : row.divisionNames()) {
                    countByDivisionName.merge(name, 1, Integer::sum);
                }
            }
        }
        List<DivisionPillDto> pills = new ArrayList<>();
        pills.add(new DivisionPillDto(null, "All", closingSoon.size()));
        for (Division division : divisionRepository.findAll()) {
            pills.add(new DivisionPillDto(division.getId().toString(), division.getName(), countByDivisionName.getOrDefault(division.getName(), 0)));
        }
        pills.add(new DivisionPillDto("none", "No division", noDivisionCount));
        return pills;
    }

    private List<Bid> applyDivisionFilter(List<Bid> allBids, String divisionFilter) {
        if (divisionFilter == null || divisionFilter.isBlank()) {
            return allBids;
        }
        if ("none".equals(divisionFilter)) {
            return allBids.stream().filter(b -> b.getLots().stream().allMatch(l -> l.getDivision() == null)).toList();
        }
        UUID divisionId = UUID.fromString(divisionFilter);
        return allBids.stream()
                .filter(b -> b.getLots().stream().anyMatch(l -> l.getDivision() != null && l.getDivision().getId().equals(divisionId)))
                .toList();
    }

    private int unpricedBidCount(List<Bid> bids) {
        int count = 0;
        for (Bid bid : bids) {
            if (bid.getStatus().isTerminal()) {
                continue;
            }
            boolean anyPriced = bid.getLots().stream().anyMatch(l -> l.getEstimatedValue() != null);
            if (!anyPriced) {
                count++;
            }
        }
        return count;
    }

    private List<ClarificationRowDto> upcomingClarifications(List<Bid> bids, Instant now) {
        List<ClarificationRowDto> rows = new ArrayList<>();
        for (Bid bid : bids) {
            if (bid.getClarificationDeadline() == null || bid.getClarificationDeadline().isBefore(now) || bid.getStatus().isTerminal()) {
                continue;
            }
            rows.add(new ClarificationRowDto(bid.getId(), bid.getTitle(), bid.getOrganization().getName(), bid.getClarificationDeadline(), DivisionNames.distinct(bid)));
        }
        rows.sort((a, b) -> a.clarificationDeadline().compareTo(b.clarificationDeadline()));
        return rows;
    }

    private List<PipelineValueRowDto> pipelineByDivision(List<Bid> bids) {
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                String label = lot.getDivision() == null ? "(no division)" : lot.getDivision().getName();
                accumulate(totals, counts, label, lot);
            }
        }
        return toRows(totals, counts);
    }

    private List<PipelineValueRowDto> pipelineByStatus(List<Bid> bids) {
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                accumulate(totals, counts, lot.getStatus().name(), lot);
            }
        }
        return toRows(totals, counts);
    }

    private void accumulate(Map<String, BigDecimal[]> totals, Map<String, Integer> counts, String label, BidLot lot) {
        if (lot.getEstimatedValue() == null || lot.getEstimatedValueCurrency() == null) {
            return;
        }
        BigDecimal[] pair = totals.computeIfAbsent(label, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        if (lot.getEstimatedValueCurrency() == Currency.ETB) {
            pair[0] = pair[0].add(lot.getEstimatedValue());
        } else {
            pair[1] = pair[1].add(lot.getEstimatedValue());
        }
        counts.merge(label, 1, Integer::sum);
    }

    private List<PipelineValueRowDto> toRows(Map<String, BigDecimal[]> totals, Map<String, Integer> counts) {
        List<PipelineValueRowDto> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : totals.entrySet()) {
            rows.add(new PipelineValueRowDto(entry.getKey(), entry.getValue()[0], entry.getValue()[1], counts.getOrDefault(entry.getKey(), 0)));
        }
        return rows;
    }

    private List<WinRateRowDto> winRateByDivision(List<Bid> bids) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> wonValues = new LinkedHashMap<>();
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getOutcome() == null) {
                    continue;
                }
                String label = lot.getDivision() == null ? "(no division)" : lot.getDivision().getName();
                tallyOutcome(counts, wonValues, label, lot);
            }
        }
        return toWinRateRows(counts, wonValues);
    }

    private List<WinRateRowDto> winRateByOfficer(List<Bid> bids) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> wonValues = new LinkedHashMap<>();
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getOutcome() == null) {
                    continue;
                }
                List<LotAccountOfficer> officers = lotAccountOfficerRepository.findById_LotId(lot.getId());
                if (officers.isEmpty()) {
                    tallyOutcome(counts, wonValues, "(unassigned)", lot);
                }
                for (LotAccountOfficer officer : officers) {
                    String label = officerName(officer.getId().getUserId());
                    tallyOutcome(counts, wonValues, label, lot);
                }
            }
        }
        return toWinRateRows(counts, wonValues);
    }

    private final Map<UUID, String> officerNameCache = new LinkedHashMap<>();

    private String officerName(UUID userId) {
        return officerNameCache.computeIfAbsent(userId, id -> appUserRepository.findById(id).map(AppUser::getFullName).orElse("(unknown)"));
    }

    private List<WinRateRowDto> winRateByOem(List<Bid> bids) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> wonValues = new LinkedHashMap<>();
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getOutcome() == null) {
                    continue;
                }
                String label = (lot.getOemBrand() == null || lot.getOemBrand().isBlank()) ? "(no OEM recorded)" : lot.getOemBrand();
                tallyOutcome(counts, wonValues, label, lot);
            }
        }
        return toWinRateRows(counts, wonValues);
    }

    private void tallyOutcome(Map<String, int[]> counts, Map<String, BigDecimal[]> wonValues, String label, BidLot lot) {
        int[] pair = counts.computeIfAbsent(label, k -> new int[]{0, 0});
        if (lot.getOutcome() == Outcome.WON) {
            pair[0]++;
        } else {
            pair[1]++;
        }
        if (lot.getOutcome() == Outcome.WON && lot.getEstimatedValue() != null && lot.getEstimatedValueCurrency() != null) {
            BigDecimal[] valuePair = wonValues.computeIfAbsent(label, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (lot.getEstimatedValueCurrency() == Currency.ETB) {
                valuePair[0] = valuePair[0].add(lot.getEstimatedValue());
            } else {
                valuePair[1] = valuePair[1].add(lot.getEstimatedValue());
            }
        }
    }

    private List<WinRateRowDto> toWinRateRows(Map<String, int[]> counts, Map<String, BigDecimal[]> wonValues) {
        List<WinRateRowDto> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int won = entry.getValue()[0];
            int lost = entry.getValue()[1];
            int total = won + lost;
            int rate = total == 0 ? 0 : Math.round(won * 100f / total);
            BigDecimal[] value = wonValues.getOrDefault(entry.getKey(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            rows.add(new WinRateRowDto(entry.getKey(), won, lost, rate, value[0], value[1]));
        }
        return rows;
    }

    private List<OutstandingBondRowDto> outstandingBonds(List<Bid> bids, LocalDate today) {
        List<OutstandingBondRowDto> rows = new ArrayList<>();
        for (Bid bid : bids) {
            if (bid.getClosingAt() == null) {
                continue;
            }
            for (BidLot lot : bid.getLots()) {
                if (lot.getBidBondAmount() == null || lot.getBidBondCurrency() == null || lot.isBidBondReturned()) {
                    continue;
                }
                LocalDate estimatedExpiry = estimateBondExpiry(bid, lot);
                boolean expiringSoon = estimatedExpiry != null && !estimatedExpiry.isBefore(today)
                        && estimatedExpiry.isBefore(today.plusDays(BOND_EXPIRING_SOON_DAYS));
                List<String> divisionNames = lot.getDivision() == null ? List.of() : List.of(lot.getDivision().getName());
                rows.add(new OutstandingBondRowDto(
                        lot.getId(), bid.getId(), bid.getTitle(), bid.getOrganization().getName(), lot.getLotLabel(),
                        lot.getBidBondAmount(), lot.getBidBondCurrency().name(), bid.getClosingAt(), estimatedExpiry, expiringSoon,
                        divisionNames));
            }
        }
        rows.sort((a, b) -> {
            if (a.estimatedExpiry() == null) return 1;
            if (b.estimatedExpiry() == null) return -1;
            return a.estimatedExpiry().compareTo(b.estimatedExpiry());
        });
        return rows;
    }

    /** Uses the real issue date once it's recorded; otherwise falls back to an estimate off the bid's opening/closing date. */
    private LocalDate estimateBondExpiry(Bid bid, BidLot lot) {
        if (lot.getBidBondValidityDays() == null) {
            return null;
        }
        if (lot.getBidBondIssueDate() != null) {
            return lot.getBidBondIssueDate().plusDays(lot.getBidBondValidityDays());
        }
        Instant base = bid.getOpeningAt() != null ? bid.getOpeningAt() : bid.getClosingAt();
        if (base == null) {
            return null;
        }
        return base.atZone(ZONE).toLocalDate().plusDays(lot.getBidBondValidityDays());
    }

    private BigDecimal sumBondsByCurrency(List<Bid> bids, Currency currency) {
        BigDecimal total = BigDecimal.ZERO;
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                if (!lot.isBidBondReturned() && lot.getBidBondAmount() != null && lot.getBidBondCurrency() == currency) {
                    total = total.add(lot.getBidBondAmount());
                }
            }
        }
        return total;
    }

    private int bondsExpiringWithinDaysCount(List<Bid> bids, LocalDate today, int withinDays) {
        LocalDate cutoff = today.plusDays(withinDays);
        int count = 0;
        for (Bid bid : bids) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getBidBondAmount() == null || lot.getBidBondCurrency() == null || lot.isBidBondReturned()) {
                    continue;
                }
                LocalDate estimatedExpiry = estimateBondExpiry(bid, lot);
                if (estimatedExpiry != null && !estimatedExpiry.isBefore(today) && estimatedExpiry.isBefore(cutoff)) {
                    count++;
                }
            }
        }
        return count;
    }
}
