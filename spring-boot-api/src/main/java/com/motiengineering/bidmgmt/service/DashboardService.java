package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
import com.motiengineering.bidmgmt.dto.ClarificationRowDto;
import com.motiengineering.bidmgmt.dto.ClosingSoonRowDto;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.dto.ExpiringDealRegistrationRowDto;
import com.motiengineering.bidmgmt.dto.OutstandingBondRowDto;
import com.motiengineering.bidmgmt.dto.PipelineValueRowDto;
import com.motiengineering.bidmgmt.dto.WinRateRowDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.util.BidRiskEvaluator;
import com.motiengineering.bidmgmt.util.DivisionNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    private static final int DEAL_REGISTRATION_EXPIRING_SOON_DAYS = 30;

    private final BidRepository bidRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final AppUserRepository appUserRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final ChecklistService checklistService;

    public DashboardDto build() {
        List<Bid> allBids = bidRepository.findAll();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);

        return new DashboardDto(
                closingWithin(allBids, now, 0, 7),
                closingWithin(allBids, now, 7, 14),
                upcomingClarifications(allBids, now),
                pipelineByDivision(allBids),
                pipelineByStatus(allBids),
                winRateByDivision(allBids),
                winRateByOfficer(allBids),
                winRateByOem(allBids),
                outstandingBonds(allBids, today),
                sumBondsByCurrency(allBids, Currency.ETB),
                sumBondsByCurrency(allBids, Currency.USD),
                expiringDealRegistrations(today));
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
            int progress = averageChecklistProgress(bid);
            String missing = BidRiskEvaluator.redFlagReason(bid, now);
            rows.add(new ClosingSoonRowDto(
                    bid.getId(), bid.getTitle(), bid.getOrganization().getName(), bid.getClosingAt(),
                    bid.getStatus().name(), progress, missing != null, missing, DivisionNames.distinct(bid)));
        }
        rows.sort((a, b) -> a.closingAt().compareTo(b.closingAt()));
        return rows;
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
}
