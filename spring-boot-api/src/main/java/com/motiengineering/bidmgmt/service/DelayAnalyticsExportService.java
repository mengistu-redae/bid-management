package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.dto.DelayAnalyticsReportDto;
import com.motiengineering.bidmgmt.dto.DelayBreakdownRowDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/** One workbook, one sheet per report section - same plain-POI approach as ReportExportService/BidExportService. */
@Service
public class DelayAnalyticsExportService {

    public byte[] toExcel(DelayAnalyticsReportDto report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);

            sheet(workbook, headerStyle, "Stage durations",
                    new String[]{"Stage", "Completed", "Avg (bd)", "Median (bd)", "P90 (bd)", "% over target"},
                    report.stageDurations().stream().map(s -> new Object[]{s.stageValue(), s.completedCount(), s.avgBusinessDays(), s.medianBusinessDays(), s.p90BusinessDays(), s.percentOverTarget()}));

            sheet(workbook, headerStyle, "Monthly trend",
                    new String[]{"Stage", "Month", "Completed", "Avg (bd)"},
                    report.monthlyTrend().stream().map(m -> new Object[]{m.stageValue(), m.yearMonth(), m.completedCount(), m.avgBusinessDays()}));

            sheet(workbook, headerStyle, "Breakdowns",
                    new String[]{"Dimension", "Key", "Completed", "Avg (bd)", "Total delay days"},
                    Stream.of(report.byDivision(), report.byOfficer(), report.byCustomer(), report.byOem())
                            .flatMap(List::stream)
                            .map((DelayBreakdownRowDto r) -> new Object[]{r.dimension(), r.key(), r.completedCount(), r.avgBusinessDays(), r.totalDelayDays()}));

            sheet(workbook, headerStyle, "Delay reason Pareto",
                    new String[]{"Code", "Label", "Occurrences", "Total delay days"},
                    report.delayReasonPareto().stream().map(r -> new Object[]{r.code(), r.label(), r.occurrences(), r.totalDelayDays()}));

            sheet(workbook, headerStyle, "Date slippage (top)",
                    new String[]{"Entity type", "Record", "Field", "Changes", "Slippage (days)"},
                    report.topDateSlippage().stream().map(r -> new Object[]{r.entityType(), r.label(), r.fieldName(), r.changeCount(), r.slippageDays()}));

            sheet(workbook, headerStyle, "Slippage by field",
                    new String[]{"Entity type", "Field", "Total changes", "Total slippage (days)"},
                    report.slippageByField().stream().map(r -> new Object[]{r.entityType(), r.fieldName(), r.totalChangeCount(), r.totalSlippageDays()}));

            sheet(workbook, headerStyle, "Waiting-on totals",
                    new String[]{"Party", "Episodes", "Total days waited"},
                    report.waitingOnTotals().stream().map(r -> new Object[]{r.partyType(), r.episodeCount(), r.totalDaysWaited()}));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        style.setFont(bold);
        return style;
    }

    private void sheet(XSSFWorkbook workbook, CellStyle headerStyle, String name, String[] headers, Stream<Object[]> rows) {
        Sheet sheet = workbook.createSheet(name);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int[] rowNum = {1};
        rows.forEach(values -> {
            Row row = sheet.createRow(rowNum[0]++);
            for (int i = 0; i < values.length; i++) {
                writeCell(row.createCell(i), values[i]);
            }
        });
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
