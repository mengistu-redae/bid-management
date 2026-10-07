package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.MonthlySummaryDto;
import com.motiengineering.bidmgmt.dto.MonthlySummaryRowDto;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the month-boundary and division-grouping rules a report that's read once a month is especially easy to get subtly wrong. */
class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private BidRepository bidRepository;
    private BidStatusHistoryRepository bidStatusHistoryRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        bidStatusHistoryRepository = mock(BidStatusHistoryRepository.class);
        when(bidRepository.findAll()).thenReturn(List.of());
        when(bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(any(), any())).thenReturn(List.of());
        service = new ReportService(bidRepository, bidStatusHistoryRepository);
    }

    private Division division(String name) {
        Division d = new Division();
        d.setId(UUID.randomUUID());
        d.setName(name);
        return d;
    }

    private BidLot lot(Division division) {
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setDivision(division);
        return lot;
    }

    private Bid bidWithLot(BidLot lot) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setOrganization(new Organization("Test Bank", Sector.BANK));
        bid.setStatus(BidStatus.PREPARING);
        lot.setBid(bid);
        bid.setLots(new ArrayList<>(List.of(lot)));
        return bid;
    }

    private Instant inMonth(int year, int month, int day) {
        return ZonedDateTime.of(year, month, day, 10, 0, 0, 0, ZONE).toInstant();
    }

    private BidStatusHistory statusChange(BidLot lot, BidStatus from, BidStatus to, Instant changedAt) {
        BidStatusHistory history = new BidStatusHistory();
        history.setLot(lot);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedAt(changedAt);
        return history;
    }

    @Test
    void countsALotAsIdentifiedOnlyWhenCreatedWithinTheRequestedMonth() {
        Division security = division("Security");
        BidLot inMonthLot = lot(security);
        inMonthLot.setCreatedAt(inMonth(2026, 3, 15));
        Bid inMonthBid = bidWithLot(inMonthLot);

        BidLot outOfMonthLot = lot(security);
        outOfMonthLot.setCreatedAt(inMonth(2026, 2, 28));
        Bid outOfMonthBid = bidWithLot(outOfMonthLot);

        when(bidRepository.findAll()).thenReturn(List.of(inMonthBid, outOfMonthBid));

        MonthlySummaryDto summary = service.monthlySummary(2026, 3);

        MonthlySummaryRowDto row = onlyRow(summary);
        assertThat(row.identified()).isEqualTo(1);
    }

    @Test
    void submittedWonLostAndDroppedComeFromLotLevelStatusHistoryNotBidLevel() {
        Division network = division("Network");
        BidLot lot = lot(network);
        Bid bid = bidWithLot(lot);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        BidStatusHistory bidLevelSubmitted = statusChange(null, BidStatus.PREPARING, BidStatus.SUBMITTED, inMonth(2026, 3, 5));
        BidStatusHistory lotLevelSubmitted = statusChange(lot, BidStatus.PREPARING, BidStatus.SUBMITTED, inMonth(2026, 3, 6));
        when(bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(any(), any()))
                .thenReturn(List.of(bidLevelSubmitted, lotLevelSubmitted));

        MonthlySummaryDto summary = service.monthlySummary(2026, 3);

        MonthlySummaryRowDto row = onlyRow(summary);
        assertThat(row.submitted()).isEqualTo(1);
    }

    @Test
    void wonValueIsSummedByCurrencyAndOnlyForWonTransitions() {
        Division datacenter = division("Datacenter Facility");
        BidLot wonEtbLot = lot(datacenter);
        wonEtbLot.setEstimatedValue(new BigDecimal("300000"));
        wonEtbLot.setEstimatedValueCurrency(Currency.ETB);
        Bid wonBid = bidWithLot(wonEtbLot);

        BidLot lostLot = lot(datacenter);
        lostLot.setEstimatedValue(new BigDecimal("999999"));
        lostLot.setEstimatedValueCurrency(Currency.ETB);
        Bid lostBid = bidWithLot(lostLot);

        when(bidRepository.findAll()).thenReturn(List.of(wonBid, lostBid));
        when(bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(any(), any())).thenReturn(List.of(
                statusChange(wonEtbLot, BidStatus.UNDER_EVALUATION, BidStatus.WON, inMonth(2026, 3, 10)),
                statusChange(lostLot, BidStatus.UNDER_EVALUATION, BidStatus.LOST, inMonth(2026, 3, 11))));

        MonthlySummaryDto summary = service.monthlySummary(2026, 3);

        MonthlySummaryRowDto row = onlyRow(summary);
        assertThat(row.won()).isEqualTo(1);
        assertThat(row.lost()).isEqualTo(1);
        assertThat(row.wonValueEtb()).isEqualByComparingTo("300000");
    }

    @Test
    void lotsWithNoDivisionAreGroupedUnderANoDivisionLabelInsteadOfBeingDropped() {
        BidLot lot = lot(null);
        lot.setCreatedAt(inMonth(2026, 3, 1));
        Bid bid = bidWithLot(lot);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        MonthlySummaryDto summary = service.monthlySummary(2026, 3);

        assertThat(summary.rows()).extracting(MonthlySummaryRowDto::divisionLabel).containsExactly("(no division)");
    }

    private MonthlySummaryRowDto onlyRow(MonthlySummaryDto summary) {
        assertThat(summary.rows()).hasSize(1);
        return summary.rows().get(0);
    }
}
