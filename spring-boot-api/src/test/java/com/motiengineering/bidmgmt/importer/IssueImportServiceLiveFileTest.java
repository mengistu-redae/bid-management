package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.ImportBatch;
import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.enums.IssueCategory;
import com.motiengineering.bidmgmt.repository.IssueRepository;
import com.motiengineering.bidmgmt.service.IssueImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real importer against docs/Issues 2026.xlsx end to end against a real Postgres - same
 * philosophy as ImportServiceLiveFilesTest (see its class javadoc for why a dedicated
 * "bidmgmt_test" database, not Testcontainers, on this machine).
 */
@SpringBootTest
@Transactional
class IssueImportServiceLiveFileTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/bidmgmt_test");
        registry.add("spring.datasource.username", () -> "bidmgmt");
        registry.add("spring.datasource.password", () -> "bidmgmt");
        registry.add("bidmgmt.seed.enabled", () -> "false");
    }

    @Autowired
    private IssueImportService issueImportService;
    @Autowired
    private IssueRepository issueRepository;

    @Test
    void importsTheRealIssuesWorkbook() throws Exception {
        List<IssueWorkbookRow> rows;
        try (FileInputStream in = new FileInputStream(Path.of("../docs/Issues 2026.xlsx").toFile())) {
            rows = issueImportService.parse(in);
        }
        assertThat(rows).hasSize(26); // 5 contract-closure + 21 operational

        ImportBatch batch = issueImportService.confirm(rows, "Issues 2026.xlsx", null);
        assertThat(batch.getRowsCreated()).isEqualTo(26);

        List<Issue> allIssues = issueRepository.findAll();
        assertThat(allIssues).hasSize(26);

        long contractClosureCount = allIssues.stream().filter(i -> i.getCategory() == IssueCategory.CONTRACT_CLOSURE).count();
        assertThat(contractClosureCount).isEqualTo(5);

        Issue cbeKiosk = allIssues.stream()
                .filter(i -> i.getCategory() == IssueCategory.CONTRACT_CLOSURE && "CBE Kiosk".equals(i.getOrganization().getName()))
                .findFirst().orElseThrow();
        assertThat(cbeKiosk.getDescription()).contains("2 years and 10 months");

        // "N&S Division" best-fit maps to the existing NETWORK division code.
        Issue occFirewall = allIssues.stream()
                .filter(i -> "OCC".equals(i.getOrganization().getName()))
                .findFirst().orElseThrow();
        assertThat(occFirewall.getDivision()).isNotNull();
        assertThat(occFirewall.getDivision().getCode()).isEqualTo("NETWORK");

        // The merged-cell forward-fill must have produced 3 separate issues for the one "CBE" customer under PCBE Issues.
        long pcbeCbeCount = allIssues.stream()
                .filter(i -> "CBE".equals(i.getOrganization().getName()) && i.getDivision() == null)
                .count();
        assertThat(pcbeCbeCount).isEqualTo(3);
    }
}
