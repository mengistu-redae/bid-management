package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ScopeType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses one sheet of "on hand bid.xlsx". Column layout (0-indexed): 0 No. |
 * 1 Bank | 2 Requested Item | 3 Other Requirements | 4 Deal size | 5 Status
 * | 6 Submission Deadline | 7 Account Officer | 8+ unlabeled extra notes
 * (the "Submitted" sheet only).
 */
public final class BidsTrackerSheetParser {

    private BidsTrackerSheetParser() {
    }

    public static List<BidsTrackerRow> parse(Sheet sheet, BidStatus sheetStatus, int defaultYear) {
        List<BidsTrackerRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            String bankRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 1));
            if (bankRaw == null || bankRaw.equalsIgnoreCase("Bank")) {
                continue; // header row, the trailing summary row (Bank is blank there), or a blank row
            }

            BidsTrackerRow r = new BidsTrackerRow();
            r.sheetName = sheet.getSheetName();
            r.sourceRowNumber = row.getRowNum() + 1;
            r.sheetStatus = sheetStatus;
            r.organizationRaw = bankRaw.replace("\n", " ").trim();

            String itemRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 2));
            if (itemRaw == null) {
                itemRaw = "(no description)";
                r.warn("Row has no requested-item description.");
            }
            ParsingUtils.RefExtraction ref = ParsingUtils.extractReferenceNumber(itemRaw.replace("\n", " "));
            r.cleanTitle = ref.cleanTitle();
            r.referenceNumber = ref.found() ? ref.referenceNumber() : null;
            r.inferredDivisionCode = DivisionInference.inferDivisionCode(r.cleanTitle);

            r.scopeTypes = parseScopeTypes(ExcelValueReader.asText(ExcelValueReader.cell(row, 3)));

            r.dealSize = ParsingUtils.parseDealSize(ExcelValueReader.asText(ExcelValueReader.cell(row, 4)));
            if (r.dealSize.needsReview()) {
                r.warn("Deal size needs manual review: " + r.dealSize.reviewNote());
            }

            r.statusNote = ExcelValueReader.asText(ExcelValueReader.cell(row, 5));

            LocalDate deadlineDate = ExcelValueReader.asDate(ExcelValueReader.cell(row, 6));
            LocalTime deadlineTime = ExcelValueReader.asTime(ExcelValueReader.cell(row, 6));
            r.submissionDeadline = deadlineDate == null ? null : deadlineDate.atTime(deadlineTime != null ? deadlineTime : LocalTime.MIDNIGHT);

            String officerRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 7));
            r.accountOfficerNames = ParsingUtils.splitNames(officerRaw);

            r.extraNotes = collectExtraNotes(row);

            rows.add(r);
        }
        return rows;
    }

    private static ScopeType scopeTypeFor(String token) {
        String t = token.toLowerCase(Locale.ROOT).trim();
        if (t.contains("deliver")) {
            return ScopeType.DELIVERY;
        }
        if (t.contains("implement")) {
            return ScopeType.IMPLEMENTATION;
        }
        if (t.contains("train")) {
            return ScopeType.TRAINING;
        }
        if (t.contains("support") || t.contains("renew")) {
            return ScopeType.SUPPORT_RENEWAL;
        }
        return null;
    }

    private static java.util.Set<ScopeType> parseScopeTypes(String text) {
        java.util.Set<ScopeType> result = java.util.EnumSet.noneOf(ScopeType.class);
        if (text == null) {
            return result;
        }
        for (String part : text.split("(?i)and|,|/")) {
            ScopeType st = scopeTypeFor(part);
            if (st != null) {
                result.add(st);
            }
        }
        return result;
    }

    private static String collectExtraNotes(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 8; col <= 20; col++) {
            String value = ExcelValueReader.asText(ExcelValueReader.cell(row, col));
            if (value != null && !value.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(value.replace("\n", " ").trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
