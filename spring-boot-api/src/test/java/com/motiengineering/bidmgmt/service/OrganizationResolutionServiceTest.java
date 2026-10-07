package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.repository.OrganizationAliasRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the organization name-variant cases named explicitly in the brief. */
class OrganizationResolutionServiceTest {

    private OrganizationRepository organizationRepository;
    private OrganizationAliasRepository organizationAliasRepository;
    private OrganizationResolutionService service;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        organizationAliasRepository = mock(OrganizationAliasRepository.class);
        when(organizationAliasRepository.findByAliasNormalized(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        service = new OrganizationResolutionService(organizationRepository, organizationAliasRepository);
    }

    private Organization cbe() {
        return new Organization("Commercial Bank of Ethiopia", Sector.BANK);
    }

    @Test
    void acronymMatchesTheFullOrganizationName() {
        when(organizationRepository.findAll()).thenReturn(List.of(cbe()));
        Optional<OrganizationResolutionService.Match> match = service.find("CBE");
        assertThat(match).isPresent();
        assertThat(match.get().organization().getName()).isEqualTo("Commercial Bank of Ethiopia");
        assertThat(match.get().confident()).isTrue();
    }

    @Test
    void caseAndWhitespaceVariantsMatchByFuzzySimilarity() {
        when(organizationRepository.findAll()).thenReturn(List.of(cbe()));
        Optional<OrganizationResolutionService.Match> match = service.find("Commercial bank of ethiopia");
        assertThat(match).isPresent();
        assertThat(match.get().confident()).isTrue();
    }

    @Test
    void aSingleLetterTypoStillMatches() {
        Organization awash = new Organization("Awash Bank", Sector.BANK);
        when(organizationRepository.findAll()).thenReturn(List.of(awash));
        Optional<OrganizationResolutionService.Match> match = service.find("Awashi Bank");
        assertThat(match).isPresent();
        assertThat(match.get().confident()).isTrue();
    }

    @Test
    void aGenuinelyDifferentOrganizationIsNotConfidentlyMerged() {
        when(organizationRepository.findAll()).thenReturn(List.of(cbe()));
        Optional<OrganizationResolutionService.Match> match = service.find("Zemen Bank");
        // present (it's the closest of the one candidate available) but not
        // confident - resolveOrCreate only reuses a confident match, so this
        // still ends up creating a brand new organization, not a bad merge.
        assertThat(match).isPresent();
        assertThat(match.get().confident()).isFalse();
    }

    @Test
    void resolveOrCreateMakesANewOrganizationWhenNoConfidentMatchExists() {
        when(organizationRepository.findAll()).thenReturn(List.of(cbe()));
        when(organizationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        Organization result = service.resolveOrCreate("Zemen Bank", Sector.BANK);
        assertThat(result.getName()).isEqualTo("Zemen Bank");
    }
}
