package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDto get() {
        return dashboardService.build();
    }
}
