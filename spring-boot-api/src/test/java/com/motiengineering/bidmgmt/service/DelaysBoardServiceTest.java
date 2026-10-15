package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
import com.motiengineering.bidmgmt.dto.DelayBoardRowDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers DelaysBoardService now that it merges BidLot, Contract and Logistics rows into one board. */
class DelaysBoardServiceTest {

    private BidRepository bidRepository;
    private ContractRepository contractRepository;
    private LogisticsShipmentRepository logisticsShipmentRepository;
    private IndicatorService indicatorService;
    private DelaysBoardService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        when(bidRepository.findAll()).thenReturn(List.of());
        contractRepository = mock(ContractRepository.class);
        when(contractRepository.findAll()).thenReturn(List.of());
        logisticsShipmentRepository = mock(LogisticsShipmentRepository.class);
        when(logisticsShipmentRepository.findAll()).thenReturn(List.of());
        LotAccountOfficerRepository lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        when(lotAccountOfficerRepository.findById_LotId(any())).thenReturn(List.of());
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        indicatorService = mock(IndicatorService.class);
        WaitingOnService waitingOnService = mock(WaitingOnService.class);
        when(waitingOnService.get(any(), any())).thenReturn(Optional.empty());
        FollowUpService followUpService = mock(FollowUpService.class);
        when(followUpService.get(any(), any())).thenReturn(Optional.empty());
        service = new DelaysBoardService(bidRepository, contractRepository, logisticsShipmentRepository,
                lotAccountOfficerRepository, appUserRepository, indicatorService, waitingOnService, followUpService);
    }

    private Bid wonBid() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test bid");
        bid.setOrganization(new Organization("Test Org", Sector.BANK));
        bid.setStatus(BidStatus.WON);
        return bid;
    }

    private void stubIndicator(IndicatorLevel level) {
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(level, 150.0, 10.0, 5.0, true, false));
    }

    @Test
    void includesACriticalContractRow() {
        Bid bid = wonBid();
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setBid(bid);
        contract.setContractNumber("CN-001");
        contract.setStatus(ContractStatus.AWARDED);
        when(contractRepository.findAll()).thenReturn(List.of(contract));
        stubIndicator(IndicatorLevel.CRITICAL);

        List<DelayBoardRowDto> rows = service.list(DelaysBoardService.Filter.EMPTY);

        assertThat(rows).hasSize(1);
        DelayBoardRowDto row = rows.get(0);
        assertThat(row.entityType()).isEqualTo("CONTRACT");
        assertThat(row.entityId()).isEqualTo(contract.getId());
        assertThat(row.detailHref()).isEqualTo("/contracts/" + contract.getId());
        assertThat(row.bidId()).isNull();
        assertThat(row.bidTitle()).isEqualTo("CN-001");
        assertThat(row.lotLabel()).isEqualTo("Test bid");
        assertThat(row.indicatorLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void excludesATerminatedContract() {
        Bid bid = wonBid();
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setBid(bid);
        contract.setStatus(ContractStatus.TERMINATED);
        when(contractRepository.findAll()).thenReturn(List.of(contract));
        stubIndicator(IndicatorLevel.CRITICAL);

        List<DelayBoardRowDto> rows = service.list(DelaysBoardService.Filter.EMPTY);

        assertThat(rows).isEmpty();
    }

    @Test
    void includesARedLogisticsRowLinkedBackToItsBid() {
        Bid bid = wonBid();
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setBid(bid);
        contract.setStatus(ContractStatus.SIGNED);

        LogisticsShipment shipment = new LogisticsShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setContract(contract);
        shipment.setShipmentMode(ShipmentMode.SEA);
        shipment.setStatus(LogisticsStatus.IN_TRANSIT);
        when(logisticsShipmentRepository.findAll()).thenReturn(List.of(shipment));
        stubIndicator(IndicatorLevel.RED);

        List<DelayBoardRowDto> rows = service.list(DelaysBoardService.Filter.EMPTY);

        assertThat(rows).hasSize(1);
        DelayBoardRowDto row = rows.get(0);
        assertThat(row.entityType()).isEqualTo("LOGISTICS");
        assertThat(row.detailHref()).isEqualTo("/logistics/" + shipment.getId());
        assertThat(row.bidTitle()).isEqualTo("SEA shipment");
        assertThat(row.lotLabel()).isEqualTo("Test bid");
        assertThat(row.organizationName()).isEqualTo("Test Org");
    }

    @Test
    void anAmberLotRowIsUnaffectedByTheNewEntityTypes() {
        Bid bid = wonBid();
        bid.setStatus(BidStatus.PREPARING);
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setLotLabel("Lot 1");
        lot.setStatus(BidStatus.PREPARING);
        bid.setLots(List.of(lot));
        when(bidRepository.findAll()).thenReturn(List.of(bid));
        stubIndicator(IndicatorLevel.AMBER);

        List<DelayBoardRowDto> rows = service.list(DelaysBoardService.Filter.EMPTY);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).entityType()).isEqualTo("LOT");
        assertThat(rows.get(0).bidId()).isEqualTo(bid.getId());
        assertThat(rows.get(0).lotLabel()).isEqualTo("Lot 1");
    }
}
