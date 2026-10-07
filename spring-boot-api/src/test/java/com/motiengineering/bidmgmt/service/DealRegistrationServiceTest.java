package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.OemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the brief's explicit "flag conflicts if another registration exists for the same customer + OEM" requirement. */
class DealRegistrationServiceTest {

    private DealRegistrationRepository dealRegistrationRepository;
    private DealRegistrationService service;

    @BeforeEach
    void setUp() {
        dealRegistrationRepository = mock(DealRegistrationRepository.class);
        service = new DealRegistrationService(
                dealRegistrationRepository,
                mock(OrganizationRepository.class),
                mock(OemRepository.class),
                mock(OpportunityRepository.class),
                mock(BidRepository.class),
                mock(OrganizationResolutionService.class),
                mock(OemService.class));
    }

    private DealRegistration registrationWithStatus(DealRegistrationStatus status) {
        DealRegistration reg = new DealRegistration();
        reg.setId(UUID.randomUUID());
        reg.setStatus(status);
        return reg;
    }

    @Test
    void flagsAnotherActiveRegistrationForTheSameCustomerAndOem() {
        UUID orgId = UUID.randomUUID();
        UUID oemId = UUID.randomUUID();
        DealRegistration existing = registrationWithStatus(DealRegistrationStatus.APPROVED);
        when(dealRegistrationRepository.findByOrganization_IdAndOem_Id(orgId, oemId)).thenReturn(List.of(existing));

        List<DealRegistration> conflicts = service.conflictsFor(orgId, oemId, UUID.randomUUID());

        assertThat(conflicts).containsExactly(existing);
    }

    @Test
    void excludesItselfFromItsOwnConflictCheck() {
        UUID orgId = UUID.randomUUID();
        UUID oemId = UUID.randomUUID();
        DealRegistration self = registrationWithStatus(DealRegistrationStatus.DRAFT);
        when(dealRegistrationRepository.findByOrganization_IdAndOem_Id(orgId, oemId)).thenReturn(List.of(self));

        List<DealRegistration> conflicts = service.conflictsFor(orgId, oemId, self.getId());

        assertThat(conflicts).isEmpty();
    }

    @Test
    void expiredAndRejectedRegistrationsAreNotConflicts() {
        UUID orgId = UUID.randomUUID();
        UUID oemId = UUID.randomUUID();
        DealRegistration expired = registrationWithStatus(DealRegistrationStatus.EXPIRED);
        DealRegistration rejected = registrationWithStatus(DealRegistrationStatus.REJECTED);
        when(dealRegistrationRepository.findByOrganization_IdAndOem_Id(orgId, oemId)).thenReturn(List.of(expired, rejected));

        List<DealRegistration> conflicts = service.conflictsFor(orgId, oemId, UUID.randomUUID());

        assertThat(conflicts).isEmpty();
    }
}
