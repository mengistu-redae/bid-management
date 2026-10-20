package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.IssueCategory;
import com.motiengineering.bidmgmt.domain.enums.IssueSeverity;
import com.motiengineering.bidmgmt.domain.enums.IssueStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.CreateIssueRequest;
import com.motiengineering.bidmgmt.dto.UpdateIssueRequest;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.IssueRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Deliberately simpler than BidStatusService/DealRegistrationService: no guarded transition
 * graph or delay-reason-on-exit rule, because an issue's workflow is a flexible lattice (e.g.
 * WAITING_ON_CUSTOMER can go back to IN_PROGRESS), not a one-way sales pipeline. The one rule
 * enforced is a common issue-tracking best practice: you can't close/resolve an issue without a
 * resolution note.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final OrganizationRepository organizationRepository;
    private final DivisionRepository divisionRepository;
    private final BidRepository bidRepository;
    private final ContractRepository contractRepository;
    private final OpportunityRepository opportunityRepository;
    private final AppUserRepository userRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final AuditService auditService;

    public Issue get(UUID id) {
        return issueRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Issue not found: " + id));
    }

    public List<Issue> list() {
        return issueRepository.findAll();
    }

    /** Other non-closed issues for the same organization+item - surfaced as a warning on the "new issue" form, not a hard block. */
    public List<Issue> openDuplicatesFor(UUID organizationId, String itemOrSystem) {
        if (organizationId == null || itemOrSystem == null || itemOrSystem.isBlank()) {
            return List.of();
        }
        return issueRepository.findOpenByOrganizationAndItem(
                organizationId, itemOrSystem, List.of(IssueStatus.RESOLVED, IssueStatus.CLOSED));
    }

    @Transactional
    public Issue create(CreateIssueRequest request, AppUser creator) {
        Issue issue = new Issue();
        issue.setCategory(request.category() == null ? IssueCategory.OPERATIONAL : IssueCategory.valueOf(request.category().toUpperCase()));
        issue.setSeverity(request.severity() == null ? IssueSeverity.MEDIUM : IssueSeverity.valueOf(request.severity().toUpperCase()));
        issue.setOrganization(resolveOrganization(request.organizationId(), request.organizationName(), request.sector()));
        if (request.divisionId() != null) {
            divisionRepository.findById(request.divisionId()).ifPresent(issue::setDivision);
        }
        issue.setItemOrSystem(request.itemOrSystem());
        issue.setDescription(request.description());
        issue.setCurrentStatusNote(request.currentStatusNote());
        issue.setNextAction(request.nextAction());
        issue.setChallenge(request.challenge());
        if (request.bidId() != null) {
            bidRepository.findById(request.bidId()).ifPresent(issue::setBid);
        }
        if (request.contractId() != null) {
            contractRepository.findById(request.contractId()).ifPresent(issue::setContract);
        }
        if (request.opportunityId() != null) {
            opportunityRepository.findById(request.opportunityId()).ifPresent(issue::setOpportunity);
        }
        issue.setFinancialImpact(request.financialImpact());
        if (request.financialImpactCurrency() != null) {
            issue.setFinancialImpactCurrency(com.motiengineering.bidmgmt.domain.enums.Currency.valueOf(request.financialImpactCurrency().toUpperCase()));
        }
        issue.setOpenedAt(request.openedAt() != null ? request.openedAt() : Instant.now());
        if (request.assignedToId() != null) {
            userRepository.findById(request.assignedToId()).ifPresent(issue::setAssignedTo);
        }
        issue.setCreatedBy(creator);
        return issueRepository.save(issue);
    }

    @Transactional
    public Issue update(UUID id, UpdateIssueRequest request, AppUser actingUser) {
        Issue issue = get(id);

        if (request.status() != null) {
            IssueStatus oldStatus = issue.getStatus();
            IssueStatus newStatus = IssueStatus.valueOf(request.status().toUpperCase());
            if (oldStatus != newStatus) {
                if (newStatus.isTerminal() && (blank(request.resolutionNotes()) && blank(issue.getResolutionNotes()))) {
                    throw new IllegalArgumentException("Resolution notes are required when resolving or closing an issue");
                }
                auditService.recordIfChanged("ISSUE", issue.getId(), "status", oldStatus, newStatus, actingUser);
                issue.setStatus(newStatus);
                issue.setResolvedAt(newStatus.isTerminal() ? Instant.now() : null);
            }
        }
        if (request.severity() != null) {
            IssueSeverity newSeverity = IssueSeverity.valueOf(request.severity().toUpperCase());
            auditService.recordIfChanged("ISSUE", issue.getId(), "severity", issue.getSeverity(), newSeverity, actingUser);
            issue.setSeverity(newSeverity);
        }
        if (request.divisionId() != null) {
            divisionRepository.findById(request.divisionId()).ifPresent(issue::setDivision);
        }
        if (request.itemOrSystem() != null) {
            issue.setItemOrSystem(request.itemOrSystem());
        }
        if (request.description() != null) {
            issue.setDescription(request.description());
        }
        if (request.currentStatusNote() != null) {
            issue.setCurrentStatusNote(request.currentStatusNote());
        }
        if (request.nextAction() != null) {
            issue.setNextAction(request.nextAction());
        }
        if (request.challenge() != null) {
            issue.setChallenge(request.challenge());
        }
        if (request.bidId() != null) {
            bidRepository.findById(request.bidId()).ifPresent(issue::setBid);
        }
        if (request.contractId() != null) {
            contractRepository.findById(request.contractId()).ifPresent(issue::setContract);
        }
        if (request.opportunityId() != null) {
            opportunityRepository.findById(request.opportunityId()).ifPresent(issue::setOpportunity);
        }
        if (request.financialImpact() != null) {
            issue.setFinancialImpact(request.financialImpact());
        }
        if (request.financialImpactCurrency() != null) {
            issue.setFinancialImpactCurrency(com.motiengineering.bidmgmt.domain.enums.Currency.valueOf(request.financialImpactCurrency().toUpperCase()));
        }
        if (request.resolutionNotes() != null) {
            issue.setResolutionNotes(request.resolutionNotes());
        }
        if (request.assignedToId() != null) {
            AppUser oldAssignee = issue.getAssignedTo();
            userRepository.findById(request.assignedToId()).ifPresent(newAssignee -> {
                auditService.recordIfChanged("ISSUE", issue.getId(), "assignedTo",
                        oldAssignee == null ? null : oldAssignee.getFullName(), newAssignee.getFullName(), actingUser);
                issue.setAssignedTo(newAssignee);
            });
        }
        return issueRepository.save(issue);
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
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
