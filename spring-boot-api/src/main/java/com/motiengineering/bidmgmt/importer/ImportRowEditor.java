package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.dto.ImportRowEditDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the user's per-row corrections/skips (made in the preview screen)
 * onto the cached parsed rows before ImportService ever sees them - a plain
 * function over the row lists so it's testable without a cache, a token, or
 * a controller in the way.
 */
public final class ImportRowEditor {

    private ImportRowEditor() {
    }

    public static List<UpcomingBidRow> applyToUpcoming(List<UpcomingBidRow> rows, List<ImportRowEditDto> edits) {
        Map<Integer, ImportRowEditDto> byIndex = indexEdits(edits);
        List<UpcomingBidRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportRowEditDto edit = byIndex.get(i);
            if (edit != null && edit.skip()) {
                continue;
            }
            UpcomingBidRow row = rows.get(i);
            if (edit != null) {
                if (!isBlank(edit.organizationRaw())) {
                    row.organizationRaw = edit.organizationRaw().trim();
                }
                if (!isBlank(edit.cleanTitle())) {
                    row.cleanTitle = edit.cleanTitle().trim();
                }
                if (!isBlank(edit.divisionCode())) {
                    row.inferredDivisionCode = edit.divisionCode().trim();
                }
            }
            result.add(row);
        }
        return result;
    }

    public static List<BidsTrackerRow> applyToTracker(List<BidsTrackerRow> rows, List<ImportRowEditDto> edits) {
        Map<Integer, ImportRowEditDto> byIndex = indexEdits(edits);
        List<BidsTrackerRow> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportRowEditDto edit = byIndex.get(i);
            if (edit != null && edit.skip()) {
                continue;
            }
            BidsTrackerRow row = rows.get(i);
            if (edit != null) {
                if (!isBlank(edit.organizationRaw())) {
                    row.organizationRaw = edit.organizationRaw().trim();
                }
                if (!isBlank(edit.cleanTitle())) {
                    row.cleanTitle = edit.cleanTitle().trim();
                }
                if (!isBlank(edit.divisionCode())) {
                    row.inferredDivisionCode = edit.divisionCode().trim();
                }
            }
            result.add(row);
        }
        return result;
    }

    public static int countSkipped(List<ImportRowEditDto> edits) {
        return edits == null ? 0 : (int) edits.stream().filter(ImportRowEditDto::skip).count();
    }

    private static Map<Integer, ImportRowEditDto> indexEdits(List<ImportRowEditDto> edits) {
        Map<Integer, ImportRowEditDto> byIndex = new HashMap<>();
        if (edits != null) {
            for (ImportRowEditDto edit : edits) {
                byIndex.put(edit.rowIndex(), edit);
            }
        }
        return byIndex;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
