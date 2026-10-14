package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DelayAnalyticsReportDto;
import com.motiengineering.bidmgmt.service.DelayAnalyticsExportService;
import com.motiengineering.bidmgmt.service.DelayAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/delay-analytics")
@RequiredArgsConstructor
public class DelayAnalyticsController {

    private final DelayAnalyticsService delayAnalyticsService;
    private final DelayAnalyticsExportService delayAnalyticsExportService;

    @GetMapping
    public DelayAnalyticsReportDto report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return delayAnalyticsService.build(from, to);
    }

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DelayAnalyticsReportDto report = delayAnalyticsService.build(from, to);
        byte[] xlsx = delayAnalyticsExportService.toExcel(report);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"delay-analytics.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(xlsx));
    }
}
