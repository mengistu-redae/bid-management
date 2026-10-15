package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.CreateContractRequest;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers ContractService's creation gate (WON bid, one per bid), guarded status transitions, and the delay-reason-on-exit rule. */
class ContractServiceTest {

    private BidRepository bidRepository;
    private ContractRepository contractRepository;
    private IndicatorService indicatorService;
    private ContractService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        contractRepository = mock(ContractRepository.class);
        when(contractRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        indicatorService = mock(IndicatorService.class);
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.GREEN, null, null, null, true, false));
        service = new ContractService(contractRepository, bidRepository, mock(StageHistoryService.class), indicatorService);
    }

    private Bid bidWithStatus(BidStatus status) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test bid");
        bid.setOrganization(new Organization("Test Org", Sector.BANK));
        bid.setStatus(status);
        return bid;
    }

    private Contract contractFor(Bid bid, ContractStatus status) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setBid(bid);
        contract.setStatus(status);
        return contract;
    }

    @Test
    void rejectsCreatingAContractForABidThatIsNotWon() {
        Bid bid = bidWithStatus(BidStatus.PREPARING);
        when(bidRepository.findById(bid.getId())).thenReturn(Optional.of(bid));
        CreateContractRequest request = new CreateContractRequest(bid.getId(), null, null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WON");
    }

    @Test
    void rejectsASecondContractForTheSameBid() {
        Bid bid = bidWithStatus(BidStatus.WON);
        when(bidRepository.findById(bid.getId())).thenReturn(Optional.of(bid));
        Contract existing = contractFor(bid, ContractStatus.AWARDED);
        when(contractRepository.findByBid_Id(bid.getId())).thenReturn(Optional.of(existing));
        CreateContractRequest request = new CreateContractRequest(bid.getId(), null, null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(existing.getId().toString());
    }

    @Test
    void createsAContractForAWonBidWithNoExistingOne() {
        Bid bid = bidWithStatus(BidStatus.WON);
        when(bidRepository.findById(bid.getId())).thenReturn(Optional.of(bid));
        when(contractRepository.findByBid_Id(bid.getId())).thenReturn(Optional.empty());
        CreateContractRequest request = new CreateContractRequest(bid.getId(), "CN-001", null, null);

        Contract contract = service.create(request, null);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.AWARDED);
        assertThat(contract.getContractNumber()).isEqualTo("CN-001");
    }

    @Test
    void rejectsSkippingSignedFromAwardedToInDelivery() {
        Bid bid = bidWithStatus(BidStatus.WON);
        Contract contract = contractFor(bid, ContractStatus.AWARDED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.transitionStatus(contract.getId(), ContractStatus.IN_DELIVERY, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWARDED")
                .hasMessageContaining("IN_DELIVERY");
    }

    @Test
    void allowsTheNextStatusInSequenceAndStampsSignedAt() {
        Bid bid = bidWithStatus(BidStatus.WON);
        Contract contract = contractFor(bid, ContractStatus.AWARDED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));

        Contract updated = service.transitionStatus(contract.getId(), ContractStatus.SIGNED, null, null, null);

        assertThat(updated.getStatus()).isEqualTo(ContractStatus.SIGNED);
        assertThat(updated.getSignedAt()).isNotNull();
    }

    @Test
    void terminatedRequiresAReason() {
        Bid bid = bidWithStatus(BidStatus.WON);
        Contract contract = contractFor(bid, ContractStatus.AWARDED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.transitionStatus(contract.getId(), ContractStatus.TERMINATED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void requiresADelayReasonWhenExitingARedOrCriticalStage() {
        Bid bid = bidWithStatus(BidStatus.WON);
        Contract contract = contractFor(bid, ContractStatus.AWARDED);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.RED, 150.0, 20.0, 15.0, true, false));

        assertThatThrownBy(() -> service.transitionStatus(contract.getId(), ContractStatus.SIGNED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay reason");

        Contract updated = service.transitionStatus(contract.getId(), ContractStatus.SIGNED, null, "WAITING_LEGAL", null);
        assertThat(updated.getStatus()).isEqualTo(ContractStatus.SIGNED);
    }
}
