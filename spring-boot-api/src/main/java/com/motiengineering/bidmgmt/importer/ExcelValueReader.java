package com.motiengineering.bidmgmt.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Reads typed values out of a POI cell, covering the ways the two sample
 * workbooks actually store dates/times/numbers/text: a real Excel date
 * serial (POI reports these as NUMERIC with a date format), a bare number,
 * or free text that ParsingUtils has to make sense of instead.
 */
public final class ExcelValueReader {

    private ExcelValueReader() {
    }

    public static Cell cell(Row row, int columnIndex) {
        if (row == null) {
            return null;
        }
        return row.getCell(columnIndex);
    }

    /** Null, trimmed text, or the cell's number re-rendered as text - never throws. */
    public static String asText(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> {
                String v = cell.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK, _NONE -> null;
            default -> null;
        };
    }

    /** A number regardless of whether the cell is numeric or digits-as-text; null if neither. */
    public static Double asNumber(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** An Excel date serial (closing/opening/clarification dates are usually this). Null if the cell isn't a date. */
    public static LocalDate asDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return null;
    }

    /** An Excel time-of-day serial (a day fraction, e.g. 0.4166 = 10:00). Null if the cell isn't a date/time. */
    public static LocalTime asTime(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            return dt.toLocalTime();
        }
        return null;
    }
}
