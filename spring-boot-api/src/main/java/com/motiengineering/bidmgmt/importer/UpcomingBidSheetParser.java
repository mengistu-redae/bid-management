package com.motiengineering.bidmgmt.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the "Upcoming Bid *.xlsx" tender-scouting sheet. Column layout
 * (0-indexed), matching the real file (and tolerating its "Closing Data"
 * header typo - columns are read by position, never by header text):
 * 0 No. | 1 Organization | 2 Title+Ref | 3 Closing Date | 4 Closing Time |
 * 5 Opening Date | 6 Opening Time | 7 Bid Validity | 8 Bid Bond Validity |
 * 9 Bid Bond Amount | 10 Clarification Date | 11 Date &amp; Name.
 */
public final class UpcomingBidSheetParser {

    private UpcomingBidSheetParser() {
    }

    public static List<UpcomingBidRow> parse(Sheet sheet, int defaultYear) {
        List<UpcomingBidRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            String orgRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 1));
            String titleRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 2));
            if (orgRaw == null || titleRaw == null) {
                continue; // header / title / trailing blank rows
            }
            if (orgRaw.equalsIgnoreCase("Name of Organization")) {
                continue; // the header row itself
            }

            UpcomingBidRow r = new UpcomingBidRow();
            r.sourceRowNumber = row.getRowNum() + 1;
            r.organizationRaw = orgRaw.replace("\n", " ").trim();

            ParsingUtils.RefExtraction ref = ParsingUtils.extractReferenceNumber(titleRaw.replace("\n", " "));
            r.cleanTitle = ref.cleanTitle();
            r.referenceNumber = ref.referenceNumber();
            if (!ref.found()) {
                r.warn("No reference number pattern found in the title - verify against the tender document.");
            } else if (ref.needsReview()) {
                r.warn("Reference number extraction looks uncertain: \"" + ref.referenceNumber() + "\" - please verify.");
            }

            r.inferredDivisionCode = DivisionInference.inferDivisionCode(r.cleanTitle);
            if (r.inferredDivisionCode == null) {
                r.warn("Could not infer a division from the title - please pick one.");
            }

            LocalDate closingDate = readDate(row, 3);
            LocalTime closingTime = readTime(row, 4, r);
            r.closingAt = combine(closingDate, closingTime);
            if (closingDate == null) {
                r.warn("No closing date found - confirm this is a real tender (not just an RFI) before submitting against it.");
            }

            LocalDate openingDate = readDate(row, 5);
            LocalTime openingTime = readTime(row, 6, r);
            r.openingAt = combine(openingDate, openingTime);

            r.bidValidityDays = ParsingUtils.parseValidityDays(ExcelValueReader.asText(ExcelValueReader.cell(row, 7)));
            r.bidBondValidityDays = ParsingUtils.parseValidityDays(ExcelValueReader.asText(ExcelValueReader.cell(row, 8)));

            Cell bondCell = ExcelValueReader.cell(row, 9);
            Double bondNumeric = ExcelValueReader.asNumber(bondCell);
            if (bondNumeric != null) {
                r.bidBondLots.add(new ParsingUtils.LotAmount("Lot 1",
                        ParsedMoney.of(java.math.BigDecimal.valueOf(bondNumeric), com.motiengineering.bidmgmt.domain.enums.Currency.ETB)));
            } else {
                String bondText = ExcelValueReader.asText(bondCell);
                r.bidBondLots = ParsingUtils.splitLotBondAmounts(bondText);
            }
            for (ParsingUtils.LotAmount lotAmount : r.bidBondLots) {
                if (lotAmount.money().needsReview()) {
                    r.warn("Bid bond amount for " + lotAmount.label() + " needs manual review: " + lotAmount.money().reviewNote());
                }
            }

            r.clarificationDeadline = readDateOrFlexibleText(row, 10, defaultYear);

            String scoutRaw = ExcelValueReader.asText(ExcelValueReader.cell(row, 11));
            int yearHint = closingDate != null ? closingDate.getYear() : defaultYear;
            ParsingUtils.ScoutNote scoutNote = ParsingUtils.parseScoutNote(scoutRaw, yearHint);
            r.scoutedDate = scoutNote.scoutedDate();
            r.scoutNames = scoutNote.scoutNames();
            if (scoutRaw != null && scoutNote.scoutNames().isEmpty()) {
                r.warn("Could not identify the scout's name from \"" + scoutRaw + "\".");
            }

            rows.add(r);
        }
        return rows;
    }

    private static LocalDate readDate(Row row, int col) {
        return ExcelValueReader.asDate(ExcelValueReader.cell(row, col));
    }

    private static LocalDate readDateOrFlexibleText(Row row, int col, int defaultYear) {
        LocalDate date = readDate(row, col);
        if (date != null) {
            return date;
        }
        String text = ExcelValueReader.asText(ExcelValueReader.cell(row, col));
        return ParsingUtils.parseFlexibleDate(text, defaultYear);
    }

    private static LocalTime readTime(Row row, int col, UpcomingBidRow r) {
        Cell cell = ExcelValueReader.cell(row, col);
        LocalTime fromDate = ExcelValueReader.asTime(cell);
        if (fromDate != null) {
            return fromDate;
        }
        String text = ExcelValueReader.asText(cell);
        if (text == null) {
            return null;
        }
        LocalTime parsed = ParsingUtils.parseTimeText(text);
        if (parsed == null) {
            r.warn("Could not parse time value \"" + text + "\".");
        }
        return parsed;
    }

    private static LocalDateTime combine(LocalDate date, LocalTime time) {
        if (date == null) {
            return null;
        }
        return date.atTime(time != null ? time : LocalTime.MIDNIGHT);
    }
}
