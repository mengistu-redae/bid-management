package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidScout;
import com.motiengineering.bidmgmt.domain.BidScoutId;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.LotScopeType;
import com.motiengineering.bidmgmt.domain.LotScopeTypeId;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidSource;
import com.motiengineering.bidmgmt.domain.enums.GoNoGo;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.domain.enums.ScopeType;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.BidFilter;
import com.motiengineering.bidmgmt.dto.CreateBidRequest;
import com.motiengineering.bidmgmt.dto.CreateLotRequest;
import com.motiengineering.bidmgmt.dto.UpdateBidRequest;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidScoutRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
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
public class BidService {

    private final BidRepository bidRepository;
    private final BidLotRepository bidLotRepository;
    private final BidScoutRepository bidScoutRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final LotScopeTypeRepository lotScopeTypeRepository;
    private final DivisionRepository divisionRepository;
    private final OrganizationRepository organizationRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final UserResolutionService userResolutionService;
    private final ChecklistService checklistService;
    private final AuditService auditService;

    public Bid get(UUID id) {
        return bidRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Bid not found: " + id));
    }

    public List<Bid> list() {
        return bidRepository.findAll();
    }

    /** In-memory filtering (division/status live on lots, not the bid) - see BidFilter's javadoc for why this stays simple at this scale. */
    public List<Bid> list(BidFilter filter) {
        List<Bid> all = bidRepository.findAll();
        if (filter == null || filter.isEmpty()) {
            return all;
        }
        return all.stream().filter(bid -> matches(bid, filter)).toList();
    }

    private boolean matches(Bid bid, BidFilter filter) {
        if (filter.organizationId() != null && !bid.getOrganization().getId().equals(filter.organizationId())) {
            return false;
        }
        if (filter.closingFrom() != null && (bid.getClosingAt() == null || bid.getClosingAt().isBefore(filter.closingFrom()))) {
            return false;
        }
        if (filter.closingTo() != null && (bid.getClosingAt() == null || bid.getClosingAt().isAfter(filter.closingTo()))) {
            return false;
        }
        if (filter.status() != null && bid.getLots().stream().noneMatch(lot -> lot.getStatus().name().equalsIgnoreCase(filter.status()))
                && !bid.getStatus().name().equalsIgnoreCase(filter.status())) {
            return false;
        }
        if (filter.divisionId() != null && bid.getLots().stream().noneMatch(lot -> lot.getDivision() != null && lot.getDivision().getId().equals(filter.divisionId()))) {
            return false;
        }
        if (filter.officerId() != null) {
            boolean anyLotHasOfficer = bid.getLots().stream().anyMatch(lot ->
                    lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                            .anyMatch(loa -> loa.getId().getUserId().equals(filter.officerId())));
            if (!anyLotHasOfficer) {
                return false;
            }
        }
        return true;
    }

    /** Same organization + same reference number already on file - the intake form's duplicate-detection warning. */
    public List<Bid> findPossibleDuplicates(UUID organizationId, String referenceNumber) {
        if (organizationId == null || referenceNumber == null || referenceNumber.isBlank()) {
            return List.of();
        }
        return bidRepository.findByOrganization_IdAndReferenceNumberIgnoreCase(organizationId, referenceNumber);
    }

    @Transactional
    public Bid create(CreateBidRequest request, AppUser creator) {
        Organization organization = resolveOrganization(request.organizationId(), request.organizationName(), request.sector());

        Bid bid = new Bid();
        bid.setOrganization(organization);
        bid.setTitle(request.title());
        bid.setReferenceNumber(request.referenceNumber());
        bid.setSource(parseSourceOrDefault(request.source()));
        bid.setScoutedDate(request.scoutedDate());
        bid.setClosingAt(request.closingAt());
        bid.setOpeningAt(request.openingAt());
        bid.setClarificationDeadline(request.clarificationDeadline());
        bid.setBidValidityDays(request.bidValidityDays());
        bid.setNotes(request.notes());
        bid.setCreatedBy(creator);
        // Set before the first save (not mutated afterward) - mutating an
        // already-persisted-but-unflushed entity and calling save() a
        // second time makes Spring Data JPA's isNew() check (ID already
        // assigned by @UuidGenerator) route through merge() instead of
        // persist(), which schedules an UPDATE instead of an INSERT - and
        // @CreationTimestamp only fires for INSERT, so created_at ends up
        // NULL on that UPDATE and the DB's NOT NULL constraint rejects it.
        // Found live via OpportunityService.convertToBid - see
        // OpportunityServiceLiveTest for the regression test.
        if (request.opportunityId() != null) {
            opportunityRepository.findById(request.opportunityId()).ifPresent(bid::setOpportunity);
        }
        bid = bidRepository.save(bid);

        if (request.scoutNames() != null) {
            for (String name : request.scoutNames()) {
                AppUser scout = userResolutionService.resolveOrCreate(name, Role.SCOUT);
                bidScoutRepository.save(new BidScout(new BidScoutId(bid.getId(), scout.getId())));
            }
        }

        CreateLotRequest lotRequest = request.firstLot() != null ? request.firstLot() : new CreateLotRequest(
                "Lot 1", null, null, null, null, null, null, null, null, null, null, null);
        addLot(bid, lotRequest);

        return get(bid.getId());
    }

    @Transactional
    public Bid update(UUID bidId, UpdateBidRequest request, AppUser actingUser) {
        Bid bid = get(bidId);
        if (request.title() != null) {
            auditField(bid, "title", bid.getTitle(), request.title(), actingUser);
            bid.setTitle(request.title());
        }
        if (request.referenceNumber() != null) {
            auditField(bid, "referenceNumber", bid.getReferenceNumber(), request.referenceNumber(), actingUser);
            bid.setReferenceNumber(request.referenceNumber());
        }
        if (request.source() != null) {
            bid.setSource(BidSource.valueOf(request.source().toUpperCase()));
        }
        if (request.scoutedDate() != null) {
            bid.setScoutedDate(request.scoutedDate());
        }
        if (request.scoutNames() != null) {
            bidScoutRepository.deleteById_BidId(bid.getId());
            for (String name : request.scoutNames()) {
                AppUser scout = userResolutionService.resolveOrCreate(name, Role.SCOUT);
                bidScoutRepository.save(new BidScout(new BidScoutId(bid.getId(), scout.getId())));
            }
        }
        if (request.closingAt() != null) {
            auditField(bid, "closingAt", bid.getClosingAt(), request.closingAt(), actingUser);
            bid.setClosingAt(request.closingAt());
        }
        if (request.openingAt() != null) {
            bid.setOpeningAt(request.openingAt());
        }
        if (request.clarificationDeadline() != null) {
            bid.setClarificationDeadline(request.clarificationDeadline());
        }
        if (request.bidValidityDays() != null) {
            bid.setBidValidityDays(request.bidValidityDays());
        }
        if (request.goNoGoDecision() != null) {
            auditField(bid, "goNoGoDecision", bid.getGoNoGoDecision(), request.goNoGoDecision(), actingUser);
            bid.setGoNoGoDecision(GoNoGo.valueOf(request.goNoGoDecision().toUpperCase()));
        }
        if (request.goNoGoReason() != null) {
            bid.setGoNoGoReason(request.goNoGoReason());
        }
        if (request.notes() != null) {
            bid.setNotes(request.notes());
        }
        return bidRepository.save(bid);
    }

    @Transactional
    public BidLot addLot(Bid bid, CreateLotRequest request) {
        BidLot lot = new BidLot();
        lot.setBid(bid);
        lot.setLotLabel(request.lotLabel() == null || request.lotLabel().isBlank() ? nextLotLabel(bid) : request.lotLabel());
        lot.setDescription(request.description());
        if (request.divisionCode() != null) {
            divisionRepository.findByCode(request.divisionCode()).ifPresent(lot::setDivision);
        }
        lot.setEstimatedValue(request.estimatedValue());
        lot.setEstimatedValueCurrency(parseCurrency(request.estimatedValueCurrency()));
        lot.setBidBondAmount(request.bidBondAmount());
        lot.setBidBondCurrency(parseCurrency(request.bidBondCurrency()));
        lot.setBidBondValidityDays(request.bidBondValidityDays());
        if (request.bidBondForm() != null) {
            lot.setBidBondForm(com.motiengineering.bidmgmt.domain.enums.BidBondForm.valueOf(request.bidBondForm().toUpperCase()));
        }
        lot.setOemBrand(request.oemBrand());
        lot = bidLotRepository.save(lot);
        bid.getLots().add(lot); // keep the in-memory graph consistent - see ImportService's note on this same pitfall

        if (request.scopeTypes() != null) {
            for (String st : request.scopeTypes()) {
                lotScopeTypeRepository.save(new LotScopeType(new LotScopeTypeId(lot.getId(), ScopeType.valueOf(st.toUpperCase()))));
            }
        }
        if (request.accountOfficerNames() != null) {
            for (String name : request.accountOfficerNames()) {
                AppUser officer = userResolutionService.resolveOrCreate(name, Role.ACCOUNT_OFFICER);
                lotAccountOfficerRepository.save(new LotAccountOfficer(new LotAccountOfficerId(lot.getId(), officer.getId())));
            }
        }

        checklistService.seedForLot(lot);
        return lot;
    }

    private String nextLotLabel(Bid bid) {
        return "Lot " + (bid.getLots().size() + 1);
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

    private BidSource parseSourceOrDefault(String source) {
        if (source == null) {
            return BidSource.UNKNOWN;
        }
        try {
            return BidSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BidSource.UNKNOWN;
        }
    }

    private com.motiengineering.bidmgmt.domain.enums.Currency parseCurrency(String currency) {
        return currency == null ? null : com.motiengineering.bidmgmt.domain.enums.Currency.valueOf(currency.toUpperCase());
    }

    private void auditField(Bid bid, String field, Object oldValue, Object newValue, AppUser actingUser) {
        auditService.recordIfChanged("BID", bid.getId(), field, oldValue, newValue, actingUser);
    }
}
