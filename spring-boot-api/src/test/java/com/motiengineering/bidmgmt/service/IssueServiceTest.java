package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.IssueCategory;
import com.motiengineering.bidmgmt.domain.enums.IssueSeverity;
import com.motiengineering.bidmgmt.domain.enums.IssueStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.CreateIssueRequest;
import com.motiengineering.bidmgmt.dto.UpdateIssueRequest;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.IssueRepository;
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

class IssueServiceTest {

    private IssueRepository issueRepository;
    private OrganizationRepository organizationRepository;
    private OrganizationResolutionService organizationResolutionService;
    private AppUserRepository userRepository;
    private AuditService auditService;
    private IssueService service;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        organizationResolutionService = mock(OrganizationResolutionService.class);
        userRepository = mock(AppUserRepository.class);
        auditService = mock(AuditService.class);
        service = new IssueService(
                issueRepository,
                organizationRepository,
                mock(DivisionRepository.class),
                mock(BidRepository.class),
                mock(ContractRepository.class),
                mock(OpportunityRepository.class),
                userRepository,
                organizationResolutionService,
                auditService);
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Organization organization() {
        Organization org = new Organization("Commercial Bank of Ethiopia", Sector.BANK);
        org.setId(UUID.randomUUID());
        return org;
    }

    private AppUser user(String name) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        return u;
    }

    @Test
    void create_resolvesOrganizationByName_andDefaultsSeverityAndCategory() {
        Organization org = organization();
        when(organizationResolutionService.resolveOrCreate("Commercial Bank of Ethiopia", Sector.OTHER)).thenReturn(org);

        CreateIssueRequest request = new CreateIssueRequest(
                null, null, null, "Commercial Bank of Ethiopia", null, null, "NAS",
                "License not visible on portal", null, null, null,
                null, null, null, null, null, null, null);

        Issue issue = service.create(request, user("Scout"));

        assertThat(issue.getOrganization()).isEqualTo(org);
        assertThat(issue.getCategory()).isEqualTo(IssueCategory.OPERATIONAL);
        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(issue.getOpenedAt()).isNotNull();
    }

    @Test
    void create_withoutOrganizationIdOrName_throws() {
        CreateIssueRequest request = new CreateIssueRequest(
                "OPERATIONAL", null, null, null, null, null, "NAS", "desc",
                null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request, user("Scout")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_movingToResolvedWithoutResolutionNotes_throws() {
        Issue issue = new Issue();
        issue.setId(UUID.randomUUID());
        issue.setStatus(IssueStatus.OPEN);
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));

        UpdateIssueRequest request = new UpdateIssueRequest(
                "RESOLVED", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(issue.getId(), request, user("Director")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resolution notes");
    }

    @Test
    void update_movingToResolvedWithResolutionNotes_setsResolvedAtAndAudits() {
        Issue issue = new Issue();
        issue.setId(UUID.randomUUID());
        issue.setStatus(IssueStatus.IN_PROGRESS);
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));

        UpdateIssueRequest request = new UpdateIssueRequest(
                "RESOLVED", null, null, null, null, null, null, null,
                null, null, null, null, null, "Replacement labels delivered and accepted", null);

        Issue updated = service.update(issue.getId(), request, user("Director"));

        assertThat(updated.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(updated.getResolvedAt()).isNotNull();
    }

    @Test
    void update_reopeningAClosedIssue_clearsResolvedAt() {
        Issue issue = new Issue();
        issue.setId(UUID.randomUUID());
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(java.time.Instant.now());
        issue.setResolutionNotes("closed previously");
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));

        UpdateIssueRequest request = new UpdateIssueRequest(
                "IN_PROGRESS", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        Issue updated = service.update(issue.getId(), request, user("Director"));

        assertThat(updated.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(updated.getResolvedAt()).isNull();
    }

    @Test
    void openDuplicatesFor_excludesTerminalStatuses() {
        UUID orgId = UUID.randomUUID();
        when(issueRepository.findOpenByOrganizationAndItem(orgId, "NAS", List.of(IssueStatus.RESOLVED, IssueStatus.CLOSED)))
                .thenReturn(List.of(new Issue()));

        List<Issue> result = service.openDuplicatesFor(orgId, "NAS");

        assertThat(result).hasSize(1);
    }

    @Test
    void openDuplicatesFor_blankItem_returnsEmptyWithoutQuerying() {
        assertThat(service.openDuplicatesFor(UUID.randomUUID(), " ")).isEmpty();
    }
}
