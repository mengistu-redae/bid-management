package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.MonthlySummaryDto;
import com.motiengineering.bidmgmt.service.ReportExportService;
import com.motiengineering.bidmgmt.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping("/monthly-summary")
    public MonthlySummaryDto monthlySummary(@RequestParam int year, @RequestParam int month) {
        return reportService.monthlySummary(year, month);
    }

    @GetMapping("/monthly-summary/export")
    public ResponseEntity<ByteArrayResource> exportMonthlySummary(@RequestParam int year, @RequestParam int month) {
        MonthlySummaryDto summary = reportService.monthlySummary(year, month);
        byte[] xlsx = reportExportService.toExcel(summary);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"monthly-summary-" + year + "-" + month + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(xlsx));
    }
}
