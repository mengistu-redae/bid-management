package com.motiengineering.bidmgmt.importer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses "Issues *.xlsx": one sheet named like "Project issuse" (a known typo
 * in the real file - matched loosely) holding long-unclosed-contract rows,
 * and every other sheet being a dated weekly snapshot of per-customer
 * operational issues grouped under division headings. New weekly sheets get
 * added with a new date in the name each time this is re-exported, so
 * "every sheet except the contract-closure one" is parsed the same way
 * rather than hardcoding a sheet name.
 */
public final class IssuesWorkbookParser {

    private static final Pattern CONTRACT_CLOSURE_SHEET_NAME = Pattern.compile("(?i)project\\s*iss?u?se?s?");
    private static final Pattern COLUMN_HEADER_ROW = Pattern.compile("(?i)^s\\.?\\s?n\\.?$");

    private IssuesWorkbookParser() {
    }

    public static List<IssueWorkbookRow> parse(Workbook wb) {
        List<IssueWorkbookRow> rows = new ArrayList<>();
        for (Sheet sheet : wb) {
            if (CONTRACT_CLOSURE_SHEET_NAME.matcher(sheet.getSheetName().trim()).matches()) {
                rows.addAll(parseContractClosureSheet(sheet));
            } else {
                rows.addAll(parseOperationalSheet(sheet));
            }
        }
        return rows;
    }

    /** Columns (0-indexed): 0 No. | 1 Contract Names | 2 Contract signing date | 3 Remark. */
    private static List<IssueWorkbookRow> parseContractClosureSheet(Sheet sheet) {
        List<IssueWorkbookRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            String contractName = ExcelValueReader.asText(ExcelValueReader.cell(row, 1));
            if (contractName == null || contractName.equalsIgnoreCase("Contract Names")) {
                continue; // header row, title row, or blank
            }

            IssueWorkbookRow r = new IssueWorkbookRow();
            r.sheetName = sheet.getSheetName();
            r.sourceRowNumber = row.getRowNum() + 1;
            r.category = "CONTRACT_CLOSURE";
            r.organizationRaw = contractName.replace("\n", " ").trim();
            r.itemOrSystem = contractName.trim();
            r.referenceDate = ExcelValueReader.asDate(ExcelValueReader.cell(row, 2));

            String remark = ExcelValueReader.asText(ExcelValueReader.cell(row, 3));
            r.description = remark == null ? "(no remark recorded)" : remark.trim();
            if (remark == null) {
                r.warn("Row has no remark text.");
            }
            if (r.referenceDate == null) {
                r.warn("No contract signing date found - opened date will default to today.");
            }

            rows.add(r);
        }
        return rows;
    }

    /**
     * Columns (0-indexed): 0 S.N. | 1 Customer Name | 2 Item | 3 Issue | 4 Current Status |
     * 5 Next Action | 6 Challenge. A division heading is a row with only column 0 populated;
     * Customer Name/S.N. are Excel-merged across a customer's multiple issue rows, which POI (like
     * any reader) only returns on the merge's top-left cell - continuation rows forward-fill the
     * last customer name seen since the current division heading.
     */
    private static List<IssueWorkbookRow> parseOperationalSheet(Sheet sheet) {
        List<IssueWorkbookRow> rows = new ArrayList<>();
        String currentDivisionLabel = null;
        String currentCustomer = null;

        for (Row row : sheet) {
            String[] cols = new String[7];
            boolean anyPopulated = false;
            for (int i = 0; i < cols.length; i++) {
                cols[i] = ExcelValueReader.asText(ExcelValueReader.cell(row, i));
                anyPopulated |= cols[i] != null;
            }
            if (!anyPopulated) {
                continue; // blank separator row
            }

            boolean onlyFirstColumnPopulated = cols[0] != null;
            for (int i = 1; i < cols.length; i++) {
                onlyFirstColumnPopulated &= cols[i] == null;
            }
            if (onlyFirstColumnPopulated) {
                String label = cols[0].replace("\n", " ").trim();
                if (!label.equalsIgnoreCase("Issue")) { // a stray one-word sub-heading some blocks carry - not a real division change
                    currentDivisionLabel = label;
                    currentCustomer = null;
                }
                continue;
            }

            if (COLUMN_HEADER_ROW.matcher(cols[0] == null ? "" : cols[0].trim()).matches()) {
                continue; // "S.N" / "S.N." column-header row
            }

            IssueWorkbookRow r = new IssueWorkbookRow();
            r.sheetName = sheet.getSheetName();
            r.sourceRowNumber = row.getRowNum() + 1;
            r.category = "OPERATIONAL";
            r.divisionSectionLabel = currentDivisionLabel;

            if (cols[1] != null) {
                currentCustomer = cols[1].replace("\n", " ").trim();
            }
            if (currentCustomer == null) {
                r.warn("No customer name found for this row or anything to forward-fill from.");
            }
            r.organizationRaw = currentCustomer == null ? "(unspecified customer)" : currentCustomer;

            r.itemOrSystem = cols[2];
            if (cols[3] == null) {
                r.description = "(no issue description recorded)";
                r.warn("Row has no issue description.");
            } else {
                r.description = cols[3].trim();
            }
            r.currentStatusNote = cols[4];
            r.nextAction = cols[5];
            r.challenge = cols[6];

            rows.add(r);
        }
        return rows;
    }
}
