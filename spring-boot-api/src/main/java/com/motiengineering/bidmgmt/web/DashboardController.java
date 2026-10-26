package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.dto.RecentActivityRowDto;
import com.motiengineering.bidmgmt.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** division is omitted for "all divisions", "none" for the no-division bucket, or a division's UUID. */
    @GetMapping
    public DashboardDto get(@RequestParam(required = false) String division) {
        return dashboardService.build(division);
    }

    /** Full, uncapped feed behind the dashboard's Recent activity "View all" link - same division scoping as get(). */
    @GetMapping("/activity")
    public List<RecentActivityRowDto> activity(@RequestParam(required = false) String division) {
        return dashboardService.activityFeed(division);
    }
}
