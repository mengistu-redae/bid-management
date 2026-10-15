package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DealRegistrationService {

    /**
     * Draft -&gt; Submitted -&gt; Approved or Rejected, Approved -&gt; Expired once
     * its expiry date passes - same guarded-transition pattern as
     * BidStatusService. Rejected/Expired have no outgoing transitions,
     * matching isTerminal() and the conflict check below, which already
     * treated both as closed.
     */
    private static final Map<DealRegistrationStatus, Set<DealRegistrationStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DealRegistrationStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DealRegistrationStatus.DRAFT, EnumSet.of(DealRegistrationStatus.SUBMITTED));
        ALLOWED_TRANSITIONS.put(DealRegistrationStatus.SUBMITTED, EnumSet.of(DealRegistrationStatus.APPROVED, DealRegistrationStatus.REJECTED));
        ALLOWED_TRANSITIONS.put(DealRegistrationStatus.APPROVED, EnumSet.of(DealRegistrationStatus.EXPIRED));
        ALLOWED_TRANSITIONS.put(DealRegistrationStatus.REJECTED, EnumSet.noneOf(DealRegistrationStatus.class));
        ALLOWED_TRANSITIONS.put(DealRegistrationStatus.EXPIRED, EnumSet.noneOf(DealRegistrationStatus.class));
    }

    private final DealRegistrationRepository dealRegistrationRepository;
    private final OrganizationRepository organizationRepository;
    private final OemRepository oemRepository;
    private final OpportunityRepository opportunityRepository;
    private final BidRepository bidRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final OemService oemService;
    private final AuditService auditService;
    private final StageHistoryService stageHistoryService;
    private final IndicatorService indicatorService;

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
                .filter(r -> !r.getStatus().isTerminal())
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
    public DealRegistration update(UUID id, UpdateDealRegistrationRequest request, AppUser actingUser) {
        DealRegistration reg = get(id);
        if (request.distributor() != null) {
            reg.setDistributor(request.distributor());
        }
        if (request.registrationId() != null) {
            reg.setRegistrationId(request.registrationId());
        }
        if (request.submittedDate() != null) {
            auditService.recordIfChanged("DEAL_REGISTRATION", reg.getId(), "submittedDate", reg.getSubmittedDate(), request.submittedDate(), actingUser);
            reg.setSubmittedDate(request.submittedDate());
        }
        if (request.status() != null) {
            DealRegistrationStatus oldStatus = reg.getStatus();
            DealRegistrationStatus newStatus = DealRegistrationStatus.valueOf(request.status().toUpperCase());
            if (oldStatus != newStatus) {
                if (!ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of()).contains(newStatus)) {
                    throw new IllegalStateException("Cannot move a deal registration from " + oldStatus + " to " + newStatus);
                }
                requireDelayReasonIfRedOrCritical(reg, request.delayReasonCode());
                stageHistoryService.record(EntityType.DEAL_REGISTRATION, reg.getId(), "status", oldStatus.name(), newStatus.name(), null, request.delayReasonCode(), actingUser);
            }
            reg.setStatus(newStatus);
        }
        if (request.approvalDate() != null) {
            auditService.recordIfChanged("DEAL_REGISTRATION", reg.getId(), "approvalDate", reg.getApprovalDate(), request.approvalDate(), actingUser);
            reg.setApprovalDate(request.approvalDate());
        }
        if (request.expiryDate() != null) {
            auditService.recordIfChanged("DEAL_REGISTRATION", reg.getId(), "expiryDate", reg.getExpiryDate(), request.expiryDate(), actingUser);
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

    /**
     * Red/critical is measured against the registration's CURRENT (about-to-be-exited) status -
     * same rule as BidStatusService.transitionLot/ContractService.transitionStatus. No division
     * concept exists for a deal registration, matching DealRegistrationMapper.indicatorFor's
     * target-resolution context exactly.
     */
    private void requireDelayReasonIfRedOrCritical(DealRegistration reg, String delayReasonCode) {
        TargetContext context = new TargetContext(null, reg.getOrganization() == null ? null : reg.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult indicator = indicatorService.compute(
                EntityType.DEAL_REGISTRATION, reg.getId(), "status", reg.getStatus().name(), reg.getCreatedAt(), context, Map.of());
        if (indicator.isRedOrCritical() && (delayReasonCode == null || delayReasonCode.isBlank())) {
            throw new IllegalArgumentException("A delay reason is required when moving a deal registration out of a stage that is red or critical");
        }
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
