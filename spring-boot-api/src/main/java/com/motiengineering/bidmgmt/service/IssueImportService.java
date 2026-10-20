package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.ImportBatch;
import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.IssueCategory;
import com.motiengineering.bidmgmt.domain.enums.IssueStatus;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.importer.IssueWorkbookRow;
import com.motiengineering.bidmgmt.importer.IssuesWorkbookParser;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.ImportBatchRepository;
import com.motiengineering.bidmgmt.repository.IssueRepository;
import com.motiengineering.bidmgmt.util.TextSimilarity;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Preview/confirm for "Issues *.xlsx" (see IssuesWorkbookParser), kept out of ImportService -
 * already large covering the two bid importers - rather than growing it further.
 */
@Service
@RequiredArgsConstructor
public class IssueImportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    /** Below this title-similarity score, don't link a "Project issuse" row to an existing Contract - leave it unlinked rather than guess wrong. */
    private static final double CONTRACT_MATCH_THRESHOLD = 0.6;

    private final IssueRepository issueRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final DivisionRepository divisionRepository;
    private final ContractRepository contractRepository;
    private final ImportBatchRepository importBatchRepository;

    @Transactional(readOnly = true)
    public List<IssueWorkbookRow> parse(InputStream xlsx) throws IOException {
        List<IssueWorkbookRow> rows;
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            rows = IssuesWorkbookParser.parse(wb);
        }
        for (IssueWorkbookRow row : rows) {
            organizationResolutionService.find(row.organizationRaw)
                    .filter(OrganizationResolutionService.Match::confident)
                    .ifPresent(match -> issueRepository.findOpenByOrganizationAndItem(
                                    match.organization().getId(), row.itemOrSystem, List.of(IssueStatus.RESOLVED, IssueStatus.CLOSED))
                            .stream().findFirst()
                            .ifPresent(existing -> row.warn("Possible duplicate: an open issue already exists for "
                                    + match.organization().getName() + " / " + row.itemOrSystem)));
        }
        return rows;
    }

    @Transactional
    public ImportBatch confirm(List<IssueWorkbookRow> rows, String sourceFilename, com.motiengineering.bidmgmt.domain.AppUser importedBy) {
        ImportBatch batch = new ImportBatch();
        batch.setKind("ISSUES");
        batch.setSourceFilename(sourceFilename);
        batch.setImportedBy(importedBy);

        for (IssueWorkbookRow row : rows) {
            batch.setRowsTotal(batch.getRowsTotal() + 1);

            IssueCategory category = IssueCategory.valueOf(row.category);
            Issue issue = new Issue();
            issue.setCategory(category);
            issue.setStatus(IssueStatus.OPEN);
            issue.setOrganization(category == IssueCategory.CONTRACT_CLOSURE
                    ? resolveOrganizationFromContractName(row.organizationRaw)
                    : resolveOrganization(row.organizationRaw));
            issue.setDivision(inferDivision(row));
            issue.setItemOrSystem(row.itemOrSystem);
            issue.setDescription(row.description);
            issue.setCurrentStatusNote(row.currentStatusNote);
            issue.setNextAction(row.nextAction);
            issue.setChallenge(row.challenge);
            issue.setOpenedAt(row.referenceDate != null
                    ? row.referenceDate.atStartOfDay(ZONE).toInstant()
                    : Instant.now());
            issue.setCreatedBy(importedBy);
            if (category == IssueCategory.CONTRACT_CLOSURE) {
                findMatchingContract(issue.getOrganization(), row.itemOrSystem).ifPresent(issue::setContract);
            }

            issueRepository.save(issue);
            if (!row.warnings.isEmpty()) {
                batch.setRowsFlagged(batch.getRowsFlagged() + 1);
            }
            batch.setRowsCreated(batch.getRowsCreated() + 1);
        }

        return importBatchRepository.save(batch);
    }

    private Organization resolveOrganization(String organizationRaw) {
        return organizationResolutionService.resolveOrCreate(organizationRaw, Sector.OTHER);
    }

    /**
     * "Project issuse" has no separate customer column - the customer is embedded as a prefix of
     * the free-text contract name (e.g. "CBE Kiosk", "Awash Bank Card Perso"). Matching the whole
     * string against known organizations dilutes the similarity score too much to ever be
     * confident, so this tries shrinking word-prefixes (1 word, then 2, then 3) and takes the
     * first confident hit - "CBE" alone confidently matches "Commercial Bank of Ethiopia" via its
     * alias, "Awash Bank" matches on the first two words. Falls back to creating a new
     * organization named after the full contract name (same as any other low-confidence name)
     * when nothing confident is found, which the preview screen's existing organization-override
     * edit can then correct by hand.
     */
    private Organization resolveOrganizationFromContractName(String contractNameRaw) {
        String[] words = contractNameRaw.trim().split("\\s+");
        for (int wordCount = 1; wordCount <= Math.min(3, words.length); wordCount++) {
            String candidate = String.join(" ", java.util.Arrays.copyOfRange(words, 0, wordCount));
            java.util.Optional<Organization> match = organizationResolutionService.find(candidate)
                    .filter(OrganizationResolutionService.Match::confident)
                    .map(OrganizationResolutionService.Match::organization);
            if (match.isPresent()) {
                return match.get();
            }
        }
        return organizationResolutionService.resolveOrCreate(contractNameRaw, Sector.OTHER);
    }

    /** N&S/S&S/DCF-SS are best-fit mapped onto the existing 4-division taxonomy; anything else (e.g. "PCBE Issues") is left unset rather than guessed - unless the user picked an explicit override in the preview screen. */
    private Division inferDivision(IssueWorkbookRow row) {
        if (row.overrideDivisionCode != null && !row.overrideDivisionCode.isBlank()) {
            return divisionRepository.findByCode(row.overrideDivisionCode.trim()).orElse(null);
        }
        if (row.divisionSectionLabel == null) {
            return null;
        }
        String label = row.divisionSectionLabel.toLowerCase(Locale.ROOT);
        String code = null;
        if (label.contains("n&s")) {
            code = "NETWORK";
        } else if (label.contains("dcf") || label.contains("s&s")) {
            code = "SERVER_STORAGE";
        }
        return code == null ? null : divisionRepository.findByCode(code).orElse(null);
    }

    private java.util.Optional<Contract> findMatchingContract(Organization organization, String contractNameRaw) {
        if (contractNameRaw == null) {
            return java.util.Optional.empty();
        }
        String needle = contractNameRaw.toLowerCase(Locale.ROOT);
        Contract best = null;
        double bestScore = 0;
        for (Contract candidate : contractRepository.findAll()) {
            if (candidate.getBid() == null || candidate.getBid().getOrganization() == null
                    || !candidate.getBid().getOrganization().getId().equals(organization.getId())) {
                continue;
            }
            double score = TextSimilarity.similarity(needle, candidate.getBid().getTitle().toLowerCase(Locale.ROOT));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= CONTRACT_MATCH_THRESHOLD ? java.util.Optional.ofNullable(best) : java.util.Optional.empty();
    }
}
