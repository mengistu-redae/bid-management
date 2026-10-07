package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.dto.MonthlySummaryDto;
import com.motiengineering.bidmgmt.dto.MonthlySummaryRowDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Month;
import java.util.Locale;

/** Excel export of one monthly summary report - one row per division, matching ReportService's shape. */
@Service
public class ReportExportService {

    private static final String[] HEADERS = {
            "Division", "Identified", "Submitted", "Won", "Lost", "Dropped", "Won Value (ETB)", "Won Value (USD)"
    };

    public byte[] toExcel(MonthlySummaryDto summary) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String sheetName = Month.of(summary.month()).getDisplayName(java.time.format.TextStyle.SHORT, Locale.US) + " " + summary.year();
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (MonthlySummaryRowDto row : summary.rows()) {
                Row xlsxRow = sheet.createRow(rowNum++);
                int col = 0;
                xlsxRow.createCell(col++).setCellValue(row.divisionLabel());
                xlsxRow.createCell(col++).setCellValue(row.identified());
                xlsxRow.createCell(col++).setCellValue(row.submitted());
                xlsxRow.createCell(col++).setCellValue(row.won());
                xlsxRow.createCell(col++).setCellValue(row.lost());
                xlsxRow.createCell(col++).setCellValue(row.dropped());
                xlsxRow.createCell(col++).setCellValue(row.wonValueEtb().doubleValue());
                xlsxRow.createCell(col).setCellValue(row.wonValueUsd().doubleValue());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
