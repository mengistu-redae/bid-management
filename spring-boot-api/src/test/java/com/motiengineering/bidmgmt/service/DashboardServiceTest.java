package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.ChecklistItem;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.LotScopeType;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidBondForm;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.GoNoGo;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
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
    private LotAccountOfficerRepository lotAccountOfficerRepository;
    private LotScopeTypeRepository lotScopeTypeRepository;
    private DivisionRepository divisionRepository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        when(lotAccountOfficerRepository.findById_LotId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        DealRegistrationRepository dealRegistrationRepository = mock(DealRegistrationRepository.class);
        when(dealRegistrationRepository.findAll()).thenReturn(List.of());
        divisionRepository = mock(DivisionRepository.class);
        when(divisionRepository.findAll()).thenReturn(List.of());
        lotScopeTypeRepository = mock(LotScopeTypeRepository.class);
        when(lotScopeTypeRepository.findById_LotId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        ChecklistService checklistService = mock(ChecklistService.class);
        when(checklistService.progressPercent(org.mockito.ArgumentMatchers.any())).thenReturn(50);
        DelaysBoardService delaysBoardService = mock(DelaysBoardService.class);
        when(delaysBoardService.summary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.motiengineering.bidmgmt.dto.DelaySummaryDto(0, 0, 0, 0, 0, List.of()));
        service = new DashboardService(bidRepository, lotAccountOfficerRepository, appUserRepository, dealRegistrationRepository,
                divisionRepository, lotScopeTypeRepository, checklistService, delaysBoardService);
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

    @Test
    void readinessGapsFlagsEveryMissingSignal() {
        // No division, no officer assignment (default-mocked empty), no go-no-go
        // decision, an undone MAF item, a midnight (still-TBC) closing time, a
        // bond amount with an unverified form, and no recorded scope type.
        BidLot lot = lotWithValue(null, new BigDecimal("100"), Currency.ETB, BidStatus.PREPARING);
        ChecklistItem maf = new ChecklistItem();
        maf.setTitle("MAF from OEM");
        maf.setDone(false);
        lot.getChecklistItems().add(maf);
        lot.setBidBondAmount(new BigDecimal("500"));
        lot.setBidBondCurrency(Currency.ETB);
        lot.setBidBondForm(BidBondForm.UNKNOWN);

        Bid bid = bidWithLots("Incomplete bid", Instant.parse("2026-01-05T21:00:00Z"), BidStatus.PREPARING, lot); // 00:00 in Africa/Addis_Ababa (UTC+3)
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        DashboardDto dashboard = service.build();

        assertThat(dashboard.closingThisWeek()).isEmpty(); // outside the default 7-day window, checked via missingReadiness instead
        var row = dashboard.missingReadiness().stream().filter(r -> r.bidId().equals(bid.getId())).findFirst().orElseThrow();
        assertThat(row.readinessGaps()).containsExactlyInAnyOrder(
                "Division", "Owner", "Go / no-go", "MAF", "Closing time", "Verify bond", "Scope");
    }

    @Test
    void readinessGapsEmptyWhenBidIsFullyReady() {
        Division security = division("Security");
        BidLot lot = lotWithValue(security, new BigDecimal("100"), Currency.ETB, BidStatus.PREPARING);
        lot.setBidBondForm(BidBondForm.BANK_GUARANTEE); // no amount set, so "Verify bond" doesn't apply regardless
        when(lotAccountOfficerRepository.findById_LotId(lot.getId())).thenReturn(List.of(mock(com.motiengineering.bidmgmt.domain.LotAccountOfficer.class)));
        when(lotScopeTypeRepository.findById_LotId(lot.getId())).thenReturn(List.of(new LotScopeType()));

        Bid bid = bidWithLots("Ready bid", Instant.parse("2026-01-05T10:00:00Z"), BidStatus.PREPARING, lot); // 13:00 Addis - not midnight
        bid.setGoNoGoDecision(GoNoGo.GO);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        DashboardDto dashboard = service.build();

        assertThat(dashboard.missingReadiness()).isEmpty();
    }

    @Test
    void divisionFilterNarrowsEveryWidgetWhilePillCountsStayUnfiltered() {
        Division security = division("Security");
        Division network = division("Network");
        Instant closingSoon = Instant.now().plus(2, ChronoUnit.DAYS);

        BidLot securityLot = lotWithValue(security, new BigDecimal("100"), Currency.ETB, BidStatus.PREPARING);
        Bid securityBid = bidWithLots("Security bid", closingSoon, BidStatus.PREPARING, securityLot);

        BidLot networkLot = lotWithValue(network, new BigDecimal("200"), Currency.ETB, BidStatus.PREPARING);
        Bid networkBid = bidWithLots("Network bid", closingSoon, BidStatus.PREPARING, networkLot);

        when(bidRepository.findAll()).thenReturn(List.of(securityBid, networkBid));
        when(divisionRepository.findAll()).thenReturn(List.of(security, network));

        DashboardDto filtered = service.build(security.getId().toString());

        assertThat(filtered.closingThisWeek()).extracting("bidId").containsExactly(securityBid.getId());
        assertThat(filtered.pipelineByDivision()).extracting("groupLabel").containsExactly("Security");

        var securityPill = filtered.divisionFilters().stream().filter(p -> "Security".equals(p.label())).findFirst().orElseThrow();
        var networkPill = filtered.divisionFilters().stream().filter(p -> "Network".equals(p.label())).findFirst().orElseThrow();
        assertThat(securityPill.closingSoonCount()).isEqualTo(1);
        assertThat(networkPill.closingSoonCount()).isEqualTo(1); // unfiltered - the other division's bid still counts here
    }
}
