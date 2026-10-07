package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.dto.MonthlySummaryDto;
import com.motiengineering.bidmgmt.dto.MonthlySummaryRowDto;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly summary by division, per the brief: identified, submitted, won,
 * lost and dropped counts plus won value, for one calendar month. Like
 * DashboardService, this is all-in-memory over every bid - the same
 * deliberate simplification, read once a month rather than once a day.
 *
 * "Identified" counts lots created in the month (a lot starts life at
 * IDENTIFIED with no history row logged for that - see BidStatusService).
 * Submitted/won/lost/dropped count lot-level BidStatusHistory transitions
 * into that status within the month, which is also where division comes
 * from (a bid can span divisions, but a lot belongs to exactly one).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final BidRepository bidRepository;
    private final BidStatusHistoryRepository bidStatusHistoryRepository;

    public MonthlySummaryDto monthlySummary(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        Instant start = yearMonth.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();

        Map<String, MutableRow> byDivision = new LinkedHashMap<>();

        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getCreatedAt() != null && !lot.getCreatedAt().isBefore(start) && lot.getCreatedAt().isBefore(end)) {
                    row(byDivision, lot).identified++;
                }
            }
        }

        for (BidStatusHistory history : bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(start, end)) {
            BidLot lot = history.getLot();
            if (lot == null) {
                continue;
            }
            MutableRow row = row(byDivision, lot);
            BidStatus toStatus = history.getToStatus();
            if (toStatus == BidStatus.SUBMITTED) {
                row.submitted++;
            } else if (toStatus == BidStatus.WON) {
                row.won++;
                addWonValue(row, lot);
            } else if (toStatus == BidStatus.LOST) {
                row.lost++;
            } else if (toStatus == BidStatus.DROPPED) {
                row.dropped++;
            }
        }

        List<MonthlySummaryRowDto> rows = new ArrayList<>();
        for (Map.Entry<String, MutableRow> entry : byDivision.entrySet()) {
            MutableRow r = entry.getValue();
            rows.add(new MonthlySummaryRowDto(entry.getKey(), r.identified, r.submitted, r.won, r.lost, r.dropped, r.wonValueEtb, r.wonValueUsd));
        }
        return new MonthlySummaryDto(year, month, rows);
    }

    private MutableRow row(Map<String, MutableRow> byDivision, BidLot lot) {
        String label = lot.getDivision() == null ? "(no division)" : lot.getDivision().getName();
        return byDivision.computeIfAbsent(label, k -> new MutableRow());
    }

    private void addWonValue(MutableRow row, BidLot lot) {
        if (lot.getEstimatedValue() == null || lot.getEstimatedValueCurrency() == null) {
            return;
        }
        if (lot.getEstimatedValueCurrency() == Currency.ETB) {
            row.wonValueEtb = row.wonValueEtb.add(lot.getEstimatedValue());
        } else {
            row.wonValueUsd = row.wonValueUsd.add(lot.getEstimatedValue());
        }
    }

    private static final class MutableRow {
        int identified;
        int submitted;
        int won;
        int lost;
        int dropped;
        BigDecimal wonValueEtb = BigDecimal.ZERO;
        BigDecimal wonValueUsd = BigDecimal.ZERO;
    }
}
