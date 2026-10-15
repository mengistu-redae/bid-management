package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.IndicatorConfig;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the Contract/Logistics extension - follow-up ownership is the sole relevance gate for those two, since neither has an account-officer concept. */
class MyFollowUpsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private BidRepository bidRepository;
    private ContractRepository contractRepository;
    private LogisticsShipmentRepository logisticsShipmentRepository;
    private LotAccountOfficerRepository lotAccountOfficerRepository;
    private DelaysBoardService delaysBoardService;
    private FollowUpService followUpService;
    private IndicatorService indicatorService;
    private MyFollowUpsService service;
    private AppUser me;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        when(bidRepository.findAll()).thenReturn(List.of());
        contractRepository = mock(ContractRepository.class);
        when(contractRepository.findAll()).thenReturn(List.of());
        logisticsShipmentRepository = mock(LogisticsShipmentRepository.class);
        when(logisticsShipmentRepository.findAll()).thenReturn(List.of());
        lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        when(lotAccountOfficerRepository.findById_LotId(any())).thenReturn(List.of());
        delaysBoardService = mock(DelaysBoardService.class);
        followUpService = mock(FollowUpService.class);
        when(followUpService.get(any(), any())).thenReturn(Optional.empty());
        indicatorService = mock(IndicatorService.class);
        IndicatorConfig config = new IndicatorConfig();
        config.setAmberThresholdPercent(BigDecimal.valueOf(75));
        when(indicatorService.indicatorConfig()).thenReturn(config);

        service = new MyFollowUpsService(bidRepository, contractRepository, logisticsShipmentRepository,
                lotAccountOfficerRepository, delaysBoardService, followUpService, indicatorService);

        me = new AppUser();
        me.setId(UUID.randomUUID());
        me.setFullName("Me");
    }

    private FollowUpState ownedBy(AppUser owner, LocalDate nextFollowUpDate) {
        FollowUpState state = new FollowUpState(EntityType.CONTRACT, UUID.randomUUID());
        state.setFollowUpOwner(owner);
        state.setNextFollowUpDate(nextFollowUpDate);
        return state;
    }

    private Contract contract() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test bid");
        bid.setOrganization(new Organization("Test Org", Sector.BANK));
        bid.setStatus(BidStatus.WON);
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setBid(bid);
        contract.setStatus(ContractStatus.AWARDED);
        return contract;
    }

    @Test
    void anOverdueContractFollowUpShowsUpForItsOwner() {
        Contract contract = contract();
        when(contractRepository.findAll()).thenReturn(List.of(contract));
        FollowUpState followUp = ownedBy(me, LocalDate.now(ZONE).minusDays(2));
        when(followUpService.get(EntityType.CONTRACT, contract.getId())).thenReturn(Optional.of(followUp));
        DelayBoardRowStub row = new DelayBoardRowStub("CONTRACT", contract.getId(), "GREEN", 10.0, false);
        when(delaysBoardService.computeContractRow(contract)).thenReturn(row.toDto());

        MyFollowUpsService.MyFollowUpsDto result = service.build(me);

        assertThat(result.overdue()).hasSize(1);
        assertThat(result.overdue().get(0).entityType()).isEqualTo("CONTRACT");
        assertThat(result.dueToday()).isEmpty();
    }

    @Test
    void aContractOwnedBySomeoneElseIsExcluded() {
        Contract contract = contract();
        when(contractRepository.findAll()).thenReturn(List.of(contract));
        AppUser someoneElse = new AppUser();
        someoneElse.setId(UUID.randomUUID());
        FollowUpState followUp = ownedBy(someoneElse, LocalDate.now(ZONE).minusDays(2));
        when(followUpService.get(EntityType.CONTRACT, contract.getId())).thenReturn(Optional.of(followUp));

        MyFollowUpsService.MyFollowUpsDto result = service.build(me);

        assertThat(result.overdue()).isEmpty();
        assertThat(result.dueToday()).isEmpty();
    }

    @Test
    void aGreenContractNearThresholdOwnedByMeIsTurningAmberSoon() {
        Contract contract = contract();
        when(contractRepository.findAll()).thenReturn(List.of(contract));
        FollowUpState followUp = ownedBy(me, null);
        when(followUpService.get(EntityType.CONTRACT, contract.getId())).thenReturn(Optional.of(followUp));
        DelayBoardRowStub row = new DelayBoardRowStub("CONTRACT", contract.getId(), "GREEN", 65.0, false);
        when(delaysBoardService.computeContractRow(contract)).thenReturn(row.toDto());

        MyFollowUpsService.MyFollowUpsDto result = service.build(me);

        assertThat(result.turningAmberSoon()).hasSize(1);
    }

    @Test
    void aStaleLogisticsShipmentOwnedByMeShowsUpAsStale() {
        Contract contract = contract();
        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setContract(contract);
        shipment.setShipmentMode(ShipmentMode.SEA);
        shipment.setStatus(LogisticsStatus.IN_TRANSIT);
        when(logisticsShipmentRepository.findAll()).thenReturn(List.of(shipment));
        FollowUpState followUp = ownedBy(me, null);
        when(followUpService.get(EntityType.LOGISTICS, shipment.getId())).thenReturn(Optional.of(followUp));
        DelayBoardRowStub row = new DelayBoardRowStub("LOGISTICS", shipment.getId(), "GREEN", 10.0, true);
        when(delaysBoardService.computeLogisticsRow(shipment)).thenReturn(row.toDto());

        MyFollowUpsService.MyFollowUpsDto result = service.build(me);

        assertThat(result.stale()).hasSize(1);
        assertThat(result.stale().get(0).entityType()).isEqualTo("LOGISTICS");
    }

    /** Builds a minimal DelayBoardRowDto without needing every one of its many fields spelled out per test. */
    private record DelayBoardRowStub(String entityType, UUID entityId, String indicatorLevel, Double percentUsed, boolean stale) {
        com.motiengineering.bidmgmt.dto.DelayBoardRowDto toDto() {
            return new com.motiengineering.bidmgmt.dto.DelayBoardRowDto(
                    entityType, entityId, "/x/" + entityId, null, "Title", null, "Subtitle",
                    null, null, null, null, "STATUS", null,
                    indicatorLevel, percentUsed, 1.0, 5.0, stale, null, List.of(), null, null);
        }
    }
}
