package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.ImportBatch;
import com.motiengineering.bidmgmt.dto.ImportPreviewResponse;
import com.motiengineering.bidmgmt.dto.ImportSummaryDto;
import com.motiengineering.bidmgmt.importer.BidsTrackerRow;
import com.motiengineering.bidmgmt.importer.ImportPreviewCache;
import com.motiengineering.bidmgmt.importer.UpcomingBidRow;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Two-step import: preview parses the uploaded file and shows warnings
 * without saving anything; confirm persists the batch the preview call
 * handed back a token for. See ImportPreviewCache for why the parsed rows
 * live in memory between the two calls rather than being re-uploaded.
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final ImportPreviewCache previewCache;

    @PostMapping("/upcoming/preview")
    public ImportPreviewResponse<List<UpcomingBidRow>> previewUpcoming(@RequestParam MultipartFile file) throws IOException {
        List<UpcomingBidRow> rows = importService.parseUpcoming(file.getInputStream(), Year.now().getValue());
        UUID token = previewCache.putUpcoming(file.getOriginalFilename(), rows);
        return new ImportPreviewResponse<>(token, file.getOriginalFilename(), rows);
    }

    @PostMapping("/upcoming/confirm")
    public ImportSummaryDto confirmUpcoming(@RequestParam UUID token) {
        ImportPreviewCache.UpcomingBatch batch = previewCache.takeUpcoming(token);
        if (batch == null) {
            throw new NoSuchElementException("Import preview expired or already confirmed - please re-upload.");
        }
        ImportBatch result = importService.confirmUpcoming(batch.rows(), batch.sourceFilename(), CurrentUserContext.requireUser());
        return toDto(result);
    }

    @PostMapping("/tracker/preview")
    public ImportPreviewResponse<Map<String, List<BidsTrackerRow>>> previewTracker(@RequestParam MultipartFile file) throws IOException {
        Map<String, List<BidsTrackerRow>> rowsBySheet = importService.parseTracker(file.getInputStream(), Year.now().getValue());
        UUID token = previewCache.putTracker(file.getOriginalFilename(), rowsBySheet);
        return new ImportPreviewResponse<>(token, file.getOriginalFilename(), rowsBySheet);
    }

    @PostMapping("/tracker/confirm")
    public ImportSummaryDto confirmTracker(@RequestParam UUID token) {
        ImportPreviewCache.TrackerBatch batch = previewCache.takeTracker(token);
        if (batch == null) {
            throw new NoSuchElementException("Import preview expired or already confirmed - please re-upload.");
        }
        ImportBatch result = importService.confirmTracker(batch.rowsBySheet(), batch.sourceFilename(), CurrentUserContext.requireUser());
        return toDto(result);
    }

    private ImportSummaryDto toDto(ImportBatch batch) {
        return new ImportSummaryDto(batch.getRowsTotal(), batch.getRowsCreated(), batch.getRowsMerged(), batch.getRowsFlagged(), batch.getRowsSkipped());
    }
}
