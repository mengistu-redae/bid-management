package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.dto.DelayRowDto;
import com.motiengineering.bidmgmt.dto.DelaySummaryDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.repository.WaitingOnStateRepository;
import com.motiengineering.bidmgmt.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weekly (Monday 7am by default) top-10-most-delayed / most-red-stages /
 * most-waited-on-parties summary to the Director and every division
 * manager. "Who we're waiting on most" is a current-open-item count, not
 * a total-days figure - the deeper time-waiting analysis belongs to phase
 * 4's bottleneck analytics, which has the stage_history data to compute it
 * properly.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyBottleneckSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyBottleneckSchedulerService.class);

    private final DashboardService dashboardService;
    private final AppUserRepository appUserRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final WaitingOnStateRepository waitingOnStateRepository;
    private final NotificationService notificationService;

    @Value("${bidmgmt.notifications.director-email}")
    private String directorEmail;

    @Value("${bidmgmt.notifications.app-base-url}")
    private String appBaseUrl;

    @Scheduled(cron = "${bidmgmt.notifications.weekly-digest-cron}")
    @Transactional
    public void run() {
        log.info("Running weekly bottleneck summary");
        DelaySummaryDto summary = dashboardService.delaySummary();

        List<AlertItemDto> topDelayed = summary.topDelayed().stream()
                .map(this::toAlertItem)
                .toList();

        Map<String, Long> stageRedCounts = summary.topDelayed().stream()
                .filter(r -> "RED".equals(r.level()) || "CRITICAL".equals(r.level()))
                .collect(Collectors.groupingBy(DelayRowDto::level, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> waitingOnTotals = waitingOnStateRepository.findById_EntityType(EntityType.LOT).stream()
                .collect(Collectors.groupingBy(w -> w.getPartyType() == null ? "UNSET" : w.getPartyType().name(), LinkedHashMap::new, Collectors.counting()));

        EmailContent content = EmailTemplates.weeklyBottleneckSummary(topDelayed, stageRedCounts, waitingOnTotals, appBaseUrl);

        Set<AppUser> recipients = new LinkedHashSet<>();
        appUserRepository.findByEmailIgnoreCase(directorEmail).ifPresent(recipients::add);
        for (UserDivision userDivision : userDivisionRepository.findAll()) {
            appUserRepository.findById(userDivision.getId().getUserId())
                    .filter(u -> u.getRole() == Role.DIVISION_MANAGER)
                    .ifPresent(recipients::add);
        }

        int sent = 0;
        for (AppUser recipient : recipients) {
            notificationService.sendIfNotAlreadySent(ReminderType.WEEKLY_BOTTLENECK_SUMMARY, EntityType.BID, recipient.getId(), recipient, content);
            sent++;
        }
        log.info("Weekly bottleneck summary sent to {} recipient(s)", sent);
    }

    private AlertItemDto toAlertItem(DelayRowDto row) {
        return new AlertItemDto(row.bidTitle(), row.lotLabel() + (row.divisionName() != null ? " - " + row.divisionName() : ""),
                "/bids/" + row.bidId(), row.level() + (row.percentUsed() != null ? ", " + Math.round(row.percentUsed()) + "%" : ""));
    }
}
