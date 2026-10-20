package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidScoutRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.ImportBatchRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ImportService#flagPossibleDuplicate} - the preview-time surfacing of the same
 * org+title+reference matching {@code findMatch} already used (invisibly) at confirm time. See
 * {@link com.motiengineering.bidmgmt.importer.ImportServiceLiveFilesTest} for the full
 * parse/confirm round trip against a real database; this is a narrower unit test of just the
 * new matching/wording decision, which doesn't need a real .xlsx file or database to exercise.
 */
class ImportServiceTest {

    private BidRepository bidRepository;
    private OrganizationResolutionService organizationResolutionService;
    private ImportService service;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        organizationResolutionService = mock(OrganizationResolutionService.class);
        service = new ImportService(
                bidRepository,
                mock(BidLotRepository.class),
                mock(BidScoutRepository.class),
                mock(LotAccountOfficerRepository.class),
                mock(LotScopeTypeRepository.class),
                mock(DivisionRepository.class),
                organizationResolutionService,
                mock(UserResolutionService.class),
                mock(ChecklistService.class),
                mock(ActivityLogService.class),
                mock(ImportBatchRepository.class),
                mock(AuditService.class));
    }

    private Organization organization() {
        Organization org = new Organization("Commercial Bank of Ethiopia", Sector.BANK);
        org.setId(UUID.randomUUID());
        return org;
    }

    private Bid existingBid(Organization org, String title, String referenceNumber) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setOrganization(org);
        bid.setTitle(title);
        bid.setReferenceNumber(referenceNumber);
        return bid;
    }

    @Test
    void exactReferenceNumberMatch_warnsAsPossibleDuplicate_whenNotAutoMerging() {
        Organization org = organization();
        Bid existing = existingBid(org, "Supply of network switches", "CBE/IT/045/2026");
        when(organizationResolutionService.find("Commercial Bank of Ethiopia"))
                .thenReturn(Optional.of(new OrganizationResolutionService.Match(org, true, true)));
        when(bidRepository.findByOrganization_Id(org.getId())).thenReturn(List.of(existing));

        List<String> warnings = new ArrayList<>();
        service.flagPossibleDuplicate("Commercial Bank of Ethiopia", "Supply of network switches (different wording)",
                "CBE/IT/045/2026", warnings::add, false);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .startsWith("Possible duplicate:")
                .contains("Supply of network switches")
                .contains("CBE/IT/045/2026");
    }

    @Test
    void titleSimilarityMatch_warnsAsWillMerge_whenAutoMerging() {
        Organization org = organization();
        Bid existing = existingBid(org, "Procurement of Smart POS Device With Application", null);
        when(organizationResolutionService.find("Commercial Bank of Ethiopia"))
                .thenReturn(Optional.of(new OrganizationResolutionService.Match(org, true, true)));
        when(bidRepository.findByOrganization_Id(org.getId())).thenReturn(List.of(existing));

        List<String> warnings = new ArrayList<>();
        // No reference number on either side, so this must fall through to title similarity.
        service.flagPossibleDuplicate("Commercial Bank of Ethiopia", "Procurement of Smart POS Device With Application",
                null, warnings::add, true);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).startsWith("Will merge into existing bid:").contains("Smart POS Device");
    }

    @Test
    void noOrganizationMatch_doesNotWarn() {
        when(organizationResolutionService.find(any())).thenReturn(Optional.empty());

        List<String> warnings = new ArrayList<>();
        service.flagPossibleDuplicate("Some Brand New Customer", "Anything", "REF-1", warnings::add, false);

        assertThat(warnings).isEmpty();
    }

    @Test
    void unconfidentOrganizationMatch_doesNotWarn() {
        Organization org = organization();
        when(organizationResolutionService.find("CBE-ish"))
                .thenReturn(Optional.of(new OrganizationResolutionService.Match(org, false, false)));

        List<String> warnings = new ArrayList<>();
        service.flagPossibleDuplicate("CBE-ish", "Anything", "REF-1", warnings::add, false);

        assertThat(warnings).isEmpty();
    }

    @Test
    void confidentOrgButNoMatchingBid_doesNotWarn() {
        Organization org = organization();
        when(organizationResolutionService.find("Commercial Bank of Ethiopia"))
                .thenReturn(Optional.of(new OrganizationResolutionService.Match(org, true, true)));
        when(bidRepository.findByOrganization_Id(org.getId())).thenReturn(List.of());

        List<String> warnings = new ArrayList<>();
        service.flagPossibleDuplicate("Commercial Bank of Ethiopia", "A tender nobody has entered yet",
                "BRAND-NEW-REF", warnings::add, false);

        assertThat(warnings).isEmpty();
    }
}
