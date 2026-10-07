package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.OpportunityDivision;
import com.motiengineering.bidmgmt.domain.OpportunityDivisionId;
import com.motiengineering.bidmgmt.domain.OpportunityOem;
import com.motiengineering.bidmgmt.domain.OpportunityOemId;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.OpportunityStage;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.CreateBidRequest;
import com.motiengineering.bidmgmt.dto.CreateLotRequest;
import com.motiengineering.bidmgmt.dto.CreateOpportunityRequest;
import com.motiengineering.bidmgmt.dto.UpdateOpportunityRequest;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityOemRepository;
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
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityDivisionRepository opportunityDivisionRepository;
    private final OpportunityOemRepository opportunityOemRepository;
    private final OrganizationRepository organizationRepository;
    private final DivisionRepository divisionRepository;
    private final AppUserRepository appUserRepository;
    private final OemRepository oemRepository;
    private final BidLotRepository bidLotRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final OemService oemService;
    private final BidService bidService;

    public Opportunity get(UUID id) {
        return opportunityRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Opportunity not found: " + id));
    }

    public List<Opportunity> list() {
        return opportunityRepository.findAll();
    }

    @Transactional
    public Opportunity create(CreateOpportunityRequest request, AppUser creator) {
        Organization organization = resolveOrganization(request.organizationId(), request.organizationName(), request.sector());

        Opportunity opportunity = new Opportunity();
        opportunity.setOrganization(organization);
        opportunity.setTitle(request.title());
        opportunity.setEstimatedValue(request.estimatedValue());
        opportunity.setEstimatedValueCurrency(parseCurrency(request.estimatedValueCurrency()));
        opportunity.setExpectedTenderDate(request.expectedTenderDate());
        opportunity.setNotes(request.notes());
        opportunity.setCreatedBy(creator);
        opportunity.setOwner(request.ownerId() != null ? appUserRepository.findById(request.ownerId()).orElse(creator) : creator);
        opportunity = opportunityRepository.save(opportunity);

        applyDivisions(opportunity, request.divisionCodes());
        applyOems(opportunity, request.oemNames());

        return opportunity;
    }

    @Transactional
    public Opportunity update(UUID id, UpdateOpportunityRequest request) {
        Opportunity opportunity = get(id);
        if (request.title() != null) {
            opportunity.setTitle(request.title());
        }
        if (request.estimatedValue() != null) {
            opportunity.setEstimatedValue(request.estimatedValue());
        }
        if (request.estimatedValueCurrency() != null) {
            opportunity.setEstimatedValueCurrency(parseCurrency(request.estimatedValueCurrency()));
        }
        if (request.expectedTenderDate() != null) {
            opportunity.setExpectedTenderDate(request.expectedTenderDate());
        }
        if (request.stage() != null) {
            opportunity.setStage(OpportunityStage.valueOf(request.stage().toUpperCase()));
        }
        if (request.lostReason() != null) {
            opportunity.setLostReason(request.lostReason());
        }
        if (request.ownerId() != null) {
            appUserRepository.findById(request.ownerId()).ifPresent(opportunity::setOwner);
        }
        if (request.divisionCodes() != null) {
            applyDivisions(opportunity, request.divisionCodes());
        }
        if (request.oemNames() != null) {
            applyOems(opportunity, request.oemNames());
        }
        if (request.notes() != null) {
            opportunity.setNotes(request.notes());
        }
        return opportunityRepository.save(opportunity);
    }

    /**
     * Creates a Bid from this Opportunity. A single division carries the
     * opportunity's full estimated value onto that lot; additional
     * divisions get an empty, needs-review lot instead of a guessed value
     * split, since silently dividing the value would misrepresent the
     * pipeline - a human decides the real per-division split.
     */
    @Transactional
    public Bid convertToBid(UUID id, AppUser actingUser) {
        Opportunity opportunity = get(id);
        if (opportunity.getStage().isTerminal()) {
            throw new IllegalStateException("Cannot convert an opportunity that is already " + opportunity.getStage());
        }

        List<OpportunityDivision> divisionLinks = opportunityDivisionRepository.findById_OpportunityId(id);
        List<OpportunityOem> oemLinks = opportunityOemRepository.findById_OpportunityId(id);
        String firstOemName = oemLinks.isEmpty() ? null : oemName(oemLinks.get(0).getId().getOemId());

        CreateBidRequest bidRequest = new CreateBidRequest(
                opportunity.getOrganization().getId(), null, null,
                opportunity.getTitle(), null, "DIRECT", null, List.of(),
                null, null, null, null, opportunity.getNotes(), opportunity.getId(),
                firstLotRequest(divisionLinks, opportunity, firstOemName));

        Bid bid = bidService.create(bidRequest, actingUser);

        for (int i = 1; i < divisionLinks.size(); i++) {
            Division division = divisionRepository.findById(divisionLinks.get(i).getId().getDivisionId()).orElse(null);
            CreateLotRequest extraLot = new CreateLotRequest(
                    null, null, division != null ? division.getCode() : null,
                    null, null, null, null, null, null, null, List.of(), List.of());
            BidLot lot = bidService.addLot(bid, extraLot);
            lot.setNeedsReview(true);
            lot.setReviewNote("Converted from a multi-division opportunity - the estimated value was put on Lot 1 only; split it across lots manually.");
            bidLotRepository.save(lot);
        }

        opportunity.setStage(OpportunityStage.CONVERTED_TO_BID);
        opportunity.setConvertedBid(bid);
        opportunityRepository.save(opportunity);

        return bidService.get(bid.getId());
    }

    private CreateLotRequest firstLotRequest(List<OpportunityDivision> divisionLinks, Opportunity opportunity, String firstOemName) {
        String divisionCode = null;
        if (!divisionLinks.isEmpty()) {
            Division division = divisionRepository.findById(divisionLinks.get(0).getId().getDivisionId()).orElse(null);
            divisionCode = division != null ? division.getCode() : null;
        }
        return new CreateLotRequest(
                "Lot 1", null, divisionCode,
                opportunity.getEstimatedValue(),
                opportunity.getEstimatedValueCurrency() != null ? opportunity.getEstimatedValueCurrency().name() : null,
                null, null, null, null, firstOemName, List.of(), List.of());
    }

    private String oemName(UUID oemId) {
        return oemRepository.findById(oemId).map(Oem::getName).orElse(null);
    }

    private void applyDivisions(Opportunity opportunity, List<String> divisionCodes) {
        opportunityDivisionRepository.deleteById_OpportunityId(opportunity.getId());
        if (divisionCodes == null) {
            return;
        }
        for (String code : divisionCodes) {
            divisionRepository.findByCode(code).ifPresent(division ->
                    opportunityDivisionRepository.save(new OpportunityDivision(new OpportunityDivisionId(opportunity.getId(), division.getId()))));
        }
    }

    private void applyOems(Opportunity opportunity, List<String> oemNames) {
        opportunityOemRepository.deleteById_OpportunityId(opportunity.getId());
        if (oemNames == null) {
            return;
        }
        for (String name : oemNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Oem oem = oemService.resolveOrCreate(name);
            opportunityOemRepository.save(new OpportunityOem(new OpportunityOemId(opportunity.getId(), oem.getId())));
        }
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

    private Currency parseCurrency(String currency) {
        return currency == null ? null : Currency.valueOf(currency.toUpperCase());
    }
}
