package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import com.motiengineering.bidmgmt.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real importer against the real sample files in docs/ - not
 * synthetic fixtures - end to end against a real Postgres, exercising
 * everything the preview/confirm screens will: Excel parsing, the
 * organization alias/acronym resolver, lot splitting, and the cross-file
 * bid/lot merge. If this ever goes red against a refreshed sample file,
 * that's a real behavior change worth looking at, not a flaky test.
 */
@SpringBootTest
@Transactional
class ImportServiceLiveFilesTest {

    // Points at a dedicated "bidmgmt_test" database on the already-running
    // docker-compose Postgres (localhost:5434, see docker-compose.yml)
    // rather than spinning up a Testcontainers instance - this sandboxed
    // shell's Maven JVM can't reach the Docker socket Testcontainers needs
    // even though `docker compose` itself works fine here. A SEPARATE
    // database (not "bidmgmt", the one the real app/seed-runner uses) is
    // deliberate: @Transactional only rolls back what THIS test inserts,
    // and running against the actual dev database would see real seeded
    // data (e.g. an already-committed "Awashi Bank" bid) that was mistaken
    // for a regression here once docker-compose's own SeedDataRunner had run.
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/bidmgmt_test");
        registry.add("spring.datasource.username", () -> "bidmgmt");
        registry.add("spring.datasource.password", () -> "bidmgmt");
        registry.add("bidmgmt.seed.enabled", () -> "false");
    }

    @Autowired
    private ImportService importService;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private BidLotRepository bidLotRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void importsBothSampleFilesAndMergesAcrossThem() throws Exception {
        int year = Year.now().getValue();

        try (FileInputStream in = new FileInputStream(Path.of("../docs/Upcoming Bid Oct 07.xlsx").toFile())) {
            List<UpcomingBidRow> rows = importService.parseUpcoming(in, year);
            assertThat(rows).hasSizeGreaterThan(25);
            importService.confirmUpcoming(rows, "Upcoming Bid Oct 07.xlsx", null);
        }

        long bidsAfterUpcoming = bidRepository.count();
        assertThat(bidsAfterUpcoming).isGreaterThan(25);

        try (FileInputStream in = new FileInputStream(Path.of("../docs/on hand bid.xlsx").toFile())) {
            Map<String, List<BidsTrackerRow>> rowsBySheet = importService.parseTracker(in, year);
            assertThat(rowsBySheet).containsKeys("Bids on hand", "Submitted", "drop");
            importService.confirmTracker(rowsBySheet, "on hand bid.xlsx", null);
        }

        // CBE ("CBE Openshift" in the Submitted sheet) must resolve to the
        // same organization as "Commercial Bank of Ethiopia" (the Upcoming
        // file), not a second new row - the brief's own example case.
        List<Organization> orgs = organizationRepository.findAll();
        long cbeOrgCount = orgs.stream().filter(o -> o.getName().toLowerCase().contains("commercial bank")).count();
        assertThat(cbeOrgCount).isEqualTo(1);

        // Awash Bank's two lots (one row per lot in the tracker file) must
        // land on the SAME bid as two lots, not two separate bids.
        Organization awash = orgs.stream().filter(o -> o.getName().toLowerCase().contains("awash")).findFirst().orElseThrow();
        List<Bid> awashBids = bidRepository.findByOrganization_Id(awash.getId());
        assertThat(awashBids).hasSize(1);
        assertThat(awashBids.get(0).getLots()).hasSize(2);

        // A bid created only from the tracker file (no match in Upcoming) still gets created.
        boolean hasBerhanBankVirtualization = bidRepository.findAll().stream()
                .anyMatch(b -> b.getOrganization().getName().toLowerCase().contains("berhan")
                        && b.getTitle().toLowerCase().contains("virtualization"));
        assertThat(hasBerhanBankVirtualization).isTrue();

        // Every lot got its checklist seeded.
        List<BidLot> allLots = bidLotRepository.findAll();
        assertThat(allLots).isNotEmpty();
        assertThat(allLots).allSatisfy(lot -> assertThat(lot.getChecklistItems()).hasSize(13));
    }
}
