package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.ChecklistItem;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the currency-separation and red-flag rules the dashboard is explicitly built around in the brief. */
class DashboardServiceTest {

    private BidRepository bidRepository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        LotAccountOfficerRepository lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        when(lotAccountOfficerRepository.findById_LotId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ChecklistService checklistService = mock(ChecklistService.class);
        when(checklistService.progressPercent(org.mockito.ArgumentMatchers.any())).thenReturn(50);
        service = new DashboardService(bidRepository, lotAccountOfficerRepository, appUserRepository, checklistService);
    }

    private Organization org(String name) {
        Organization o = new Organization(name, com.motiengineering.bidmgmt.domain.enums.Sector.BANK);
        return o;
    }

    private Division division(String name) {
        Division d = new Division();
        d.setId(UUID.randomUUID());
        d.setName(name);
        return d;
    }

    private BidLot lotWithValue(Division division, BigDecimal value, Currency currency, BidStatus status) {
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setDivision(division);
        lot.setEstimatedValue(value);
        lot.setEstimatedValueCurrency(currency);
        lot.setStatus(status);
        lot.setChecklistItems(new ArrayList<>());
        return lot;
    }

    private Bid bidWithLots(String title, Instant closingAt, BidStatus status, BidLot... lots) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle(title);
        bid.setOrganization(org("Test Bank"));
        bid.setClosingAt(closingAt);
        bid.setStatus(status);
        List<BidLot> lotList = new ArrayList<>(List.of(lots));
        for (BidLot lot : lotList) {
            lot.setBid(bid);
        }
        bid.setLots(lotList);
        return bid;
    }

    @Test
    void pipelineValueNeverSumsDifferentCurrenciesTogether() {
        Division security = division("Security");
        BidLot etbLot = lotWithValue(security, new BigDecimal("1000000"), Currency.ETB, BidStatus.PREPARING);
        BidLot usdLot = lotWithValue(security, new BigDecimal("5000"), Currency.USD, BidStatus.PREPARING);
        Bid bid = bidWithLots("Test", null, BidStatus.PREPARING, etbLot, usdLot);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        DashboardDto dashboard = service.build();

        var row = dashboard.pipelineByDivision().stream().filter(r -> r.groupLabel().equals("Security")).findFirst().orElseThrow();
        assertThat(row.totalEtb()).isEqualByComparingTo("1000000");
        assertThat(row.totalUsd()).isEqualByComparingTo("5000");
    }

    @Test
    void winRateCountsOnlyDecidedLotsAndComputesPercentCorrectly() {
        Division security = division("Security");
        BidLot won = lotWithValue(security, new BigDecimal("100"), Currency.ETB, BidStatus.WON);
        won.setOutcome(Outcome.WON);
        BidLot lost = lotWithValue(security, new BigDecimal("50"), Currency.ETB, BidStatus.LOST);
        lost.setOutcome(Outcome.LOST);
        BidLot undecided = lotWithValue(security, new BigDecimal("30"), Currency.ETB, BidStatus.PREPARING);
        Bid bid = bidWithLots("Test", null, BidStatus.WON, won, lost, undecided);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        DashboardDto dashboard = service.build();

        var row = dashboard.winRateByDivision().stream().filter(r -> r.groupLabel().equals("Security")).findFirst().orElseThrow();
        assertThat(row.wonCount()).isEqualTo(1);
        assertThat(row.lostCount()).isEqualTo(1);
        assertThat(row.winRatePercent()).isEqualTo(50);
        assertThat(row.wonValueEtb()).isEqualByComparingTo("100");
    }

    @Test
    void redFlagsOnlyFireWithinFiveDaysForAnUndoneMafOrQuoteItem() {
        BidLot lot = lotWithValue(division("Security"), new BigDecimal("100"), Currency.ETB, BidStatus.PREPARING);
        ChecklistItem maf = new ChecklistItem();
        maf.setTitle("MAF from OEM");
        maf.setDone(false);
        lot.getChecklistItems().add(maf);

        Bid closingSoon = bidWithLots("Closing soon", Instant.now().plus(3, ChronoUnit.DAYS), BidStatus.PREPARING, lot);
        when(bidRepository.findAll()).thenReturn(List.of(closingSoon));

        DashboardDto dashboard = service.build();

        assertThat(dashboard.closingThisWeek()).hasSize(1);
        assertThat(dashboard.closingThisWeek().get(0).redFlag()).isTrue();
        assertThat(dashboard.closingThisWeek().get(0).redFlagReason()).isEqualTo("Missing MAF");
    }

    @Test
    void noRedFlagWhenMafAndQuoteAreBothDoneEvenIfClosingSoon() {
        BidLot lot = lotWithValue(division("Security"), new BigDecimal("100"), Currency.ETB, BidStatus.PREPARING);
        ChecklistItem maf = new ChecklistItem();
        maf.setTitle("MAF from OEM");
        maf.setDone(true);
        ChecklistItem quote = new ChecklistItem();
        quote.setTitle("Vendor/distributor quotation");
        quote.setDone(true);
        lot.getChecklistItems().add(maf);
        lot.getChecklistItems().add(quote);

        Bid closingSoon = bidWithLots("Closing soon", Instant.now().plus(2, ChronoUnit.DAYS), BidStatus.PREPARING, lot);
        when(bidRepository.findAll()).thenReturn(List.of(closingSoon));

        DashboardDto dashboard = service.build();

        assertThat(dashboard.closingThisWeek().get(0).redFlag()).isFalse();
    }

    @Test
    void outstandingBondTotalsExcludeReturnedBondsAndSeparateCurrencies() {
        BidLot returned = lotWithValue(division("Security"), null, null, BidStatus.SUBMITTED);
        returned.setBidBondAmount(new BigDecimal("500"));
        returned.setBidBondCurrency(Currency.ETB);
        returned.setBidBondReturned(true);

        BidLot outstandingEtb = lotWithValue(division("Security"), null, null, BidStatus.SUBMITTED);
        outstandingEtb.setBidBondAmount(new BigDecimal("1000"));
        outstandingEtb.setBidBondCurrency(Currency.ETB);

        BidLot outstandingUsd = lotWithValue(division("Security"), null, null, BidStatus.SUBMITTED);
        outstandingUsd.setBidBondAmount(new BigDecimal("200"));
        outstandingUsd.setBidBondCurrency(Currency.USD);

        Bid bid = bidWithLots("Test", Instant.now().minus(1, ChronoUnit.DAYS), BidStatus.SUBMITTED, returned, outstandingEtb, outstandingUsd);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        DashboardDto dashboard = service.build();

        assertThat(dashboard.outstandingBondTotalEtb()).isEqualByComparingTo("1000");
        assertThat(dashboard.outstandingBondTotalUsd()).isEqualByComparingTo("200");
        assertThat(dashboard.outstandingBonds()).hasSize(2);
    }
}
