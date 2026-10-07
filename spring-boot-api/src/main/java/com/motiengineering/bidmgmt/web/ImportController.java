package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.ImportBatch;
import com.motiengineering.bidmgmt.dto.ConfirmTrackerRequest;
import com.motiengineering.bidmgmt.dto.ConfirmUpcomingRequest;
import com.motiengineering.bidmgmt.dto.ImportPreviewResponse;
import com.motiengineering.bidmgmt.dto.ImportRowEditDto;
import com.motiengineering.bidmgmt.dto.ImportSummaryDto;
import com.motiengineering.bidmgmt.importer.BidsTrackerRow;
import com.motiengineering.bidmgmt.importer.ImportPreviewCache;
import com.motiengineering.bidmgmt.importer.ImportRowEditor;
import com.motiengineering.bidmgmt.importer.UpcomingBidRow;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Two-step import: preview parses the uploaded file and shows warnings
 * without saving anything; confirm persists the batch the preview call
 * handed back a token for, after applying any per-row corrections/skips the
 * user made in the preview screen (see ImportRowEditor) - replacing the
 * original "confirm persists the cached preview exactly as parsed, no way
 * to fix a row by hand" gap. See ImportPreviewCache for why the parsed rows
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
    public ImportSummaryDto confirmUpcoming(@RequestBody ConfirmUpcomingRequest request) {
        ImportPreviewCache.UpcomingBatch batch = previewCache.takeUpcoming(request.token());
        if (batch == null) {
            throw new NoSuchElementException("Import preview expired or already confirmed - please re-upload.");
        }
        List<UpcomingBidRow> rows = ImportRowEditor.applyToUpcoming(batch.rows(), request.edits());
        int manuallySkipped = ImportRowEditor.countSkipped(request.edits());

        ImportBatch result = importService.confirmUpcoming(rows, batch.sourceFilename(), CurrentUserContext.requireUser());
        return toDto(result, manuallySkipped);
    }

    @PostMapping("/tracker/preview")
    public ImportPreviewResponse<Map<String, List<BidsTrackerRow>>> previewTracker(@RequestParam MultipartFile file) throws IOException {
        Map<String, List<BidsTrackerRow>> rowsBySheet = importService.parseTracker(file.getInputStream(), Year.now().getValue());
        UUID token = previewCache.putTracker(file.getOriginalFilename(), rowsBySheet);
        return new ImportPreviewResponse<>(token, file.getOriginalFilename(), rowsBySheet);
    }

    @PostMapping("/tracker/confirm")
    public ImportSummaryDto confirmTracker(@RequestBody ConfirmTrackerRequest request) {
        ImportPreviewCache.TrackerBatch batch = previewCache.takeTracker(request.token());
        if (batch == null) {
            throw new NoSuchElementException("Import preview expired or already confirmed - please re-upload.");
        }
        Map<String, List<ImportRowEditDto>> editsBySheet = request.editsBySheet() == null ? Map.of() : request.editsBySheet();
        int manuallySkipped = 0;
        Map<String, List<BidsTrackerRow>> rowsBySheet = new LinkedHashMap<>();
        for (Map.Entry<String, List<BidsTrackerRow>> entry : batch.rowsBySheet().entrySet()) {
            List<ImportRowEditDto> edits = editsBySheet.getOrDefault(entry.getKey(), List.of());
            rowsBySheet.put(entry.getKey(), ImportRowEditor.applyToTracker(entry.getValue(), edits));
            manuallySkipped += ImportRowEditor.countSkipped(edits);
        }

        ImportBatch result = importService.confirmTracker(rowsBySheet, batch.sourceFilename(), CurrentUserContext.requireUser());
        return toDto(result, manuallySkipped);
    }

    private ImportSummaryDto toDto(ImportBatch batch, int manuallySkipped) {
        return new ImportSummaryDto(
                batch.getRowsTotal() + manuallySkipped,
                batch.getRowsCreated(),
                batch.getRowsMerged(),
                batch.getRowsFlagged(),
                batch.getRowsSkipped() + manuallySkipped);
    }
}
