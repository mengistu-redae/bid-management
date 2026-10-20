package com.motiengineering.bidmgmt.importer;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses the real docs/Issues 2026.xlsx - not a synthetic fixture - the same philosophy
 * ImportServiceLiveFilesTest uses for the other two sample files: if this ever goes red against a
 * refreshed export of the sheet, that's a real parsing regression worth looking at.
 */
class IssuesWorkbookParserTest {

    private List<IssueWorkbookRow> parseRealFile() throws Exception {
        try (FileInputStream in = new FileInputStream(Path.of("../docs/Issues 2026.xlsx").toFile());
             Workbook wb = WorkbookFactory.create(in)) {
            return IssuesWorkbookParser.parse(wb);
        }
    }

    @Test
    void parsesBothSheetShapes() throws Exception {
        List<IssueWorkbookRow> rows = parseRealFile();

        // 5 contract-closure rows ("Project issuse") + 21 operational data rows ("October 9 2026":
        // rows 3,4,5, 11-15, 20, 23-34 - the last block's "Demo kiosk" appears twice, a genuine
        // duplicate in the source data, not a parsing bug).
        long contractClosureCount = rows.stream().filter(r -> r.category.equals("CONTRACT_CLOSURE")).count();
        long operationalCount = rows.stream().filter(r -> r.category.equals("OPERATIONAL")).count();
        assertThat(contractClosureCount).isEqualTo(5);
        assertThat(operationalCount).isEqualTo(21);
    }

    @Test
    void contractClosureRow_capturesNameSigningDateAndRemark() throws Exception {
        IssueWorkbookRow cbeKiosk = parseRealFile().stream()
                .filter(r -> r.category.equals("CONTRACT_CLOSURE") && r.organizationRaw.equals("CBE Kiosk"))
                .findFirst().orElseThrow();

        assertThat(cbeKiosk.referenceDate).isEqualTo(LocalDate.of(2023, 12, 11));
        assertThat(cbeKiosk.description).contains("2 years and 10 months");
        assertThat(cbeKiosk.warnings).isEmpty();
    }

    @Test
    void operationalRow_pullsDivisionFromSectionHeadingNotColumnHeader() throws Exception {
        IssueWorkbookRow occFirewall = parseRealFile().stream()
                .filter(r -> r.category.equals("OPERATIONAL") && r.organizationRaw.equals("OCC"))
                .findFirst().orElseThrow();

        assertThat(occFirewall.divisionSectionLabel).startsWith("N&S Division");
        assertThat(occFirewall.itemOrSystem).isEqualTo("Firewall");
        assertThat(occFirewall.description).contains("advance payment");
    }

    @Test
    void operationalRow_forwardFillsMergedCustomerNameAcrossMultipleItems() throws Exception {
        List<IssueWorkbookRow> cbeRows = parseRealFile().stream()
                .filter(r -> r.category.equals("OPERATIONAL") && r.organizationRaw.equals("CBE")
                        && "PCBE  Issues".equals(r.divisionSectionLabel))
                .toList();

        // Digital Branch (Kiosk), CVM, Bulk deposit - three items under one merged "CBE" customer cell.
        assertThat(cbeRows).hasSize(3);
        assertThat(cbeRows).extracting(r -> r.itemOrSystem)
                .containsExactly("Digital Branch (Kiosk)", "CVM", "Bulk deposit (10Qty)");
    }

    @Test
    void operationalRow_skipsTheStrayIssueSubHeadingWithoutLosingDivisionContext() throws Exception {
        IssueWorkbookRow awashNas = parseRealFile().stream()
                .filter(r -> r.category.equals("OPERATIONAL") && r.organizationRaw.equals("Awash Bank")
                        && "NAS".equals(r.itemOrSystem))
                .findFirst().orElseThrow();

        // S&S Division's block has a stray lone "Issue" row between the division heading and the
        // column-header row - it must not be misread as a second division heading.
        assertThat(awashNas.divisionSectionLabel).isEqualTo("S&S Division");
    }
}
