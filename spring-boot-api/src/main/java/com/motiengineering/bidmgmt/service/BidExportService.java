package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.dto.BidDto;
import com.motiengineering.bidmgmt.dto.LotDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Excel export of any filtered bid list - one row per lot, since that's where division/value/officer actually live. */
@Service
public class BidExportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private static final String[] HEADERS = {
            "Organization", "Title", "Reference Number", "Lot", "Division", "Status", "Closing Date",
            "Estimated Value", "Currency", "Bid Bond Amount", "Bond Currency", "OEM/Brand", "Account Officer(s)", "Outcome"
    };

    public byte[] toExcel(List<BidDto> bids) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bids");
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
            for (BidDto bid : bids) {
                for (LotDto lot : bid.lots()) {
                    Row row = sheet.createRow(rowNum++);
                    int col = 0;
                    row.createCell(col++).setCellValue(bid.organization() != null ? bid.organization().name() : "");
                    row.createCell(col++).setCellValue(bid.title());
                    row.createCell(col++).setCellValue(bid.referenceNumber() != null ? bid.referenceNumber() : "");
                    row.createCell(col++).setCellValue(lot.lotLabel());
                    row.createCell(col++).setCellValue(lot.division() != null ? lot.division().name() : "");
                    row.createCell(col++).setCellValue(lot.status() != null ? lot.status() : bid.status());
                    row.createCell(col++).setCellValue(formatDate(bid.closingAt()));
                    if (lot.estimatedValue() != null) {
                        row.createCell(col).setCellValue(lot.estimatedValue().doubleValue());
                    }
                    col++;
                    row.createCell(col++).setCellValue(lot.estimatedValueCurrency() != null ? lot.estimatedValueCurrency() : "");
                    if (lot.bidBondAmount() != null) {
                        row.createCell(col).setCellValue(lot.bidBondAmount().doubleValue());
                    }
                    col++;
                    row.createCell(col++).setCellValue(lot.bidBondCurrency() != null ? lot.bidBondCurrency() : "");
                    row.createCell(col++).setCellValue(lot.oemBrand() != null ? lot.oemBrand() : "");
                    row.createCell(col++).setCellValue(String.join(", ", lot.accountOfficers().stream().map(o -> o.fullName()).toList()));
                    row.createCell(col).setCellValue(lot.outcome() != null ? lot.outcome() : "");
                }
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

    private String formatDate(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_FMT.format(instant.atZone(ZONE));
    }
}
