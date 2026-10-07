package com.motiengineering.bidmgmt;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.List;

/**
 * Loads the two sample spreadsheets into an empty database on first run,
 * per the brief's "seed script that loads the sample files so I can see
 * real data on first run." Runs through the exact same preview/confirm
 * parsing path a real admin import would use - never a shortcut.
 */
@Component
@RequiredArgsConstructor
public class SeedDataRunner implements ApplicationRunner {

    private final BidRepository bidRepository;
    private final AppUserRepository appUserRepository;
    private final ImportService importService;

    @Value("${bidmgmt.seed.enabled}")
    private boolean enabled;

    @Value("${bidmgmt.seed.upcoming-file}")
    private String upcomingFilePath;

    @Value("${bidmgmt.seed.tracker-file}")
    private String trackerFilePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled || bidRepository.count() > 0) {
            return;
        }

        AppUser director = appUserRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.DIRECTOR)
                .findFirst()
                .orElse(null);

        importIfPresent(upcomingFilePath, director, true);
        importIfPresent(trackerFilePath, director, false);
    }

    private void importIfPresent(String path, AppUser director, boolean upcoming) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            System.out.println("[seed] Skipping " + path + " - file not found (set BIDMGMT_SEED_UPCOMING/BIDMGMT_SEED_TRACKER to override).");
            return;
        }
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            int year = Year.now().getValue();
            if (upcoming) {
                List<com.motiengineering.bidmgmt.importer.UpcomingBidRow> rows = importService.parseUpcoming(in, year);
                importService.confirmUpcoming(rows, file.getFileName().toString(), director);
                System.out.println("[seed] Imported " + rows.size() + " rows from " + path);
            } else {
                var rowsBySheet = importService.parseTracker(in, year);
                importService.confirmTracker(rowsBySheet, file.getFileName().toString(), director);
                System.out.println("[seed] Imported tracker sheets from " + path);
            }
        }
    }
}
