package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.OpportunityStage;
import com.motiengineering.bidmgmt.dto.UpdateOpportunityRequest;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.OemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityOemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers OpportunityService's guarded stage transitions - same pattern as BidStatusServiceTest/DealRegistrationServiceTest. */
class OpportunityServiceTest {

    private OpportunityRepository opportunityRepository;
    private OpportunityDivisionRepository opportunityDivisionRepository;
    private IndicatorService indicatorService;
    private OpportunityService service;

    @BeforeEach
    void setUp() {
        opportunityRepository = mock(OpportunityRepository.class);
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        opportunityDivisionRepository = mock(OpportunityDivisionRepository.class);
        when(opportunityDivisionRepository.findById_OpportunityId(any())).thenReturn(List.of());
        indicatorService = mock(IndicatorService.class);
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.GREEN, null, null, null, true, false));
        service = new OpportunityService(
                opportunityRepository,
                opportunityDivisionRepository,
                mock(OpportunityOemRepository.class),
                mock(OrganizationRepository.class),
                mock(DivisionRepository.class),
                mock(AppUserRepository.class),
                mock(OemRepository.class),
                mock(BidLotRepository.class),
                mock(OrganizationResolutionService.class),
                mock(OemService.class),
                mock(BidService.class),
                mock(AuditService.class),
                mock(StageHistoryService.class),
                indicatorService);
    }

    private Opportunity opportunityWithStage(OpportunityStage stage) {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(UUID.randomUUID());
        opportunity.setStage(stage);
        return opportunity;
    }

    private UpdateOpportunityRequest stageOnly(String stage) {
        return new UpdateOpportunityRequest(null, null, null, null, stage, null, null, null, null, null, null);
    }

    @Test
    void rejectsSkippingStagesFromLeadToExpectingTender() {
        Opportunity lead = opportunityWithStage(OpportunityStage.LEAD);
        when(opportunityRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.update(lead.getId(), stageOnly("EXPECTING_TENDER"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEAD")
                .hasMessageContaining("EXPECTING_TENDER");
    }

    @Test
    void allowsTheNextStageInSequence() {
        Opportunity lead = opportunityWithStage(OpportunityStage.LEAD);
        when(opportunityRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        Opportunity updated = service.update(lead.getId(), stageOnly("QUALIFIED"), null);

        assertThat(updated.getStage()).isEqualTo(OpportunityStage.QUALIFIED);
    }

    @Test
    void rejectsSettingConvertedToBidDirectly() {
        Opportunity expecting = opportunityWithStage(OpportunityStage.EXPECTING_TENDER);
        when(opportunityRepository.findById(expecting.getId())).thenReturn(Optional.of(expecting));

        assertThatThrownBy(() -> service.update(expecting.getId(), stageOnly("CONVERTED_TO_BID"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lostClosedRequiresAReason() {
        Opportunity qualified = opportunityWithStage(OpportunityStage.QUALIFIED);
        when(opportunityRepository.findById(qualified.getId())).thenReturn(Optional.of(qualified));

        assertThatThrownBy(() -> service.update(qualified.getId(), stageOnly("LOST_CLOSED"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void lostClosedSucceedsWhenAReasonIsSuppliedInTheSameRequest() {
        Opportunity qualified = opportunityWithStage(OpportunityStage.QUALIFIED);
        when(opportunityRepository.findById(qualified.getId())).thenReturn(Optional.of(qualified));
        UpdateOpportunityRequest request = new UpdateOpportunityRequest(null, null, null, null, "LOST_CLOSED", "Lost to a cheaper competitor", null, null, null, null, null);

        Opportunity updated = service.update(qualified.getId(), request, null);

        assertThat(updated.getStage()).isEqualTo(OpportunityStage.LOST_CLOSED);
    }

    @Test
    void terminalStagesHaveNoOutgoingTransitions() {
        Opportunity lost = opportunityWithStage(OpportunityStage.LOST_CLOSED);
        when(opportunityRepository.findById(lost.getId())).thenReturn(Optional.of(lost));

        assertThatThrownBy(() -> service.update(lost.getId(), stageOnly("LEAD"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void movingOutOfARedStageWithoutADelayReasonIsRejected() {
        Opportunity qualified = opportunityWithStage(OpportunityStage.QUALIFIED);
        when(opportunityRepository.findById(qualified.getId())).thenReturn(Optional.of(qualified));
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.RED, 150.0, 10.0, 5.0, true, false));

        assertThatThrownBy(() -> service.update(qualified.getId(), stageOnly("RFI_PROPOSAL"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay reason");
    }

    @Test
    void movingOutOfARedStageSucceedsWithADelayReason() {
        Opportunity qualified = opportunityWithStage(OpportunityStage.QUALIFIED);
        when(opportunityRepository.findById(qualified.getId())).thenReturn(Optional.of(qualified));
        when(indicatorService.compute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(IndicatorLevel.RED, 150.0, 10.0, 5.0, true, false));
        UpdateOpportunityRequest request = new UpdateOpportunityRequest(null, null, null, null, "RFI_PROPOSAL", null, null, null, null, null, "OEM_MAF_PENDING");

        Opportunity updated = service.update(qualified.getId(), request, null);

        assertThat(updated.getStage()).isEqualTo(OpportunityStage.RFI_PROPOSAL);
    }
}
