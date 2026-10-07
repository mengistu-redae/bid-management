package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.CreateDealRegistrationRequest;
import com.motiengineering.bidmgmt.dto.UpdateDealRegistrationRequest;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.OemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DealRegistrationService {

    private final DealRegistrationRepository dealRegistrationRepository;
    private final OrganizationRepository organizationRepository;
    private final OemRepository oemRepository;
    private final OpportunityRepository opportunityRepository;
    private final BidRepository bidRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final OemService oemService;

    public DealRegistration get(UUID id) {
        return dealRegistrationRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Deal registration not found: " + id));
    }

    public List<DealRegistration> list() {
        return dealRegistrationRepository.findAll();
    }

    /** Other registrations for the same customer + OEM, excluding this one - the brief's "flag conflicts" requirement. */
    public List<DealRegistration> conflictsFor(UUID organizationId, UUID oemId, UUID excludingId) {
        return dealRegistrationRepository.findByOrganization_IdAndOem_Id(organizationId, oemId).stream()
                .filter(r -> !r.getId().equals(excludingId))
                .filter(r -> r.getStatus() != DealRegistrationStatus.REJECTED && r.getStatus() != DealRegistrationStatus.EXPIRED)
                .toList();
    }

    @Transactional
    public DealRegistration create(CreateDealRegistrationRequest request, AppUser creator) {
        Oem oem = resolveOem(request.oemId(), request.oemName());
        Organization organization = resolveOrganization(request.organizationId(), request.organizationName(), request.sector());

        DealRegistration reg = new DealRegistration();
        reg.setOem(oem);
        reg.setOrganization(organization);
        reg.setDistributor(request.distributor());
        reg.setRegistrationId(request.registrationId());
        if (request.opportunityId() != null) {
            opportunityRepository.findById(request.opportunityId()).ifPresent(reg::setOpportunity);
        }
        if (request.bidId() != null) {
            bidRepository.findById(request.bidId()).ifPresent(reg::setBid);
        }
        reg.setSubmittedDate(request.submittedDate());
        reg.setStatus(request.status() != null ? DealRegistrationStatus.valueOf(request.status().toUpperCase()) : DealRegistrationStatus.DRAFT);
        reg.setApprovalDate(request.approvalDate());
        reg.setExpiryDate(request.expiryDate());
        reg.setProtectedDiscountPercent(request.protectedDiscountPercent());
        reg.setSpecialPriceReference(request.specialPriceReference());
        reg.setNotes(request.notes());
        reg.setCreatedBy(creator);
        return dealRegistrationRepository.save(reg);
    }

    @Transactional
    public DealRegistration update(UUID id, UpdateDealRegistrationRequest request) {
        DealRegistration reg = get(id);
        if (request.distributor() != null) {
            reg.setDistributor(request.distributor());
        }
        if (request.registrationId() != null) {
            reg.setRegistrationId(request.registrationId());
        }
        if (request.submittedDate() != null) {
            reg.setSubmittedDate(request.submittedDate());
        }
        if (request.status() != null) {
            reg.setStatus(DealRegistrationStatus.valueOf(request.status().toUpperCase()));
        }
        if (request.approvalDate() != null) {
            reg.setApprovalDate(request.approvalDate());
        }
        if (request.expiryDate() != null) {
            reg.setExpiryDate(request.expiryDate());
        }
        if (request.protectedDiscountPercent() != null) {
            reg.setProtectedDiscountPercent(request.protectedDiscountPercent());
        }
        if (request.specialPriceReference() != null) {
            reg.setSpecialPriceReference(request.specialPriceReference());
        }
        if (request.notes() != null) {
            reg.setNotes(request.notes());
        }
        return dealRegistrationRepository.save(reg);
    }

    private Oem resolveOem(UUID oemId, String oemName) {
        if (oemId != null) {
            return oemRepository.findById(oemId).orElseThrow(() -> new NoSuchElementException("OEM not found: " + oemId));
        }
        if (oemName == null || oemName.isBlank()) {
            throw new IllegalArgumentException("Either oemId or oemName is required");
        }
        return oemService.resolveOrCreate(oemName);
    }

    private Organization resolveOrganization(UUID organizationId, String organizationName, String sector) {
        if (organizationId != null) {
            return organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new NoSuchElementException("Organization not found: " + organizationId));
        }
        if (organizationName == null || organizationName.isBlank()) {
            throw new IllegalArgumentException("Either organizationId or organizationName is required");
        }
        Sector sectorEnum = sector == null ? Sector.OTHER : Sector.valueOf(sector.toUpperCase());
        return organizationResolutionService.resolveOrCreate(organizationName, sectorEnum);
    }
}
