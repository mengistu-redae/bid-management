package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.FollowUpStateRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Daily per-user digest (owners + division managers + Director), running
 * on the same cron as ReminderSchedulerService's existing jobs. Source
 * data is the last 24h of alert_state rows (so "newly amber/red" and
 * "newly stale" mean exactly what DelayAlertSchedulerService already
 * decided was worth a fresh alert, not a daily rehash of every open item)
 * plus due-today-or-earlier follow-ups. Scoped to BidLot, same as the
 * alert scheduler.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyDigestSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestSchedulerService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final AppUserRepository appUserRepository;
    private final BidLotRepository bidLotRepository;
    private final AlertStateRepository alertStateRepository;
    private final FollowUpStateRepository followUpStateRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final NotificationService notificationService;

    @Value("${bidmgmt.notifications.director-email}")
    private String directorEmail;

    @Value("${bidmgmt.notifications.app-base-url}")
    private String appBaseUrl;

    private record Bucket(List<AlertItemDto> dueFollowUps, List<AlertItemDto> newlyAmberRed, List<AlertItemDto> stale) {
        static Bucket empty() {
            return new Bucket(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    @Scheduled(cron = "${bidmgmt.notifications.reminder-cron}")
    @Transactional
    public void run() {
        log.info("Running daily per-user digest job");
        Map<UUID, Bucket> buckets = new LinkedHashMap<>();
        Set<AppUser> directors = new LinkedHashSet<>();
        appUserRepository.findByEmailIgnoreCase(directorEmail).ifPresent(directors::add);
        int criticalCount = 0;

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        for (AlertState state : alertStateRepository.findById_AlertKindAndLastSentAtAfter(AlertKind.INDICATOR, since)) {
            BidLot lot = bidLotRepository.findById(state.getId().getEntityId()).orElse(null);
            if (lot == null || lot.getBid() == null) {
                continue;
            }
            AlertItemDto item = itemFor(lot);
            boolean critical = "CRITICAL".equals(state.getLastLevel());
            if (critical) {
                criticalCount++;
            }
            for (AppUser owner : officersForLot(lot)) {
                buckets.computeIfAbsent(owner.getId(), k -> Bucket.empty()).newlyAmberRed().add(item);
            }
            for (AppUser manager : managersForLot(lot)) {
                buckets.computeIfAbsent(manager.getId(), k -> Bucket.empty()).newlyAmberRed().add(item);
            }
            for (AppUser director : directors) {
                buckets.computeIfAbsent(director.getId(), k -> Bucket.empty()).newlyAmberRed().add(item);
            }
        }

        for (AlertState state : alertStateRepository.findById_AlertKindAndLastSentAtAfter(AlertKind.STALE, since)) {
            BidLot lot = bidLotRepository.findById(state.getId().getEntityId()).orElse(null);
            if (lot == null || lot.getBid() == null) {
                continue;
            }
            AlertItemDto item = itemFor(lot);
            for (AppUser owner : officersForLot(lot)) {
                buckets.computeIfAbsent(owner.getId(), k -> Bucket.empty()).stale().add(item);
            }
            if ("STALE_DOUBLE".equals(state.getLastLevel())) {
                for (AppUser manager : managersForLot(lot)) {
                    buckets.computeIfAbsent(manager.getId(), k -> Bucket.empty()).stale().add(item);
                }
            }
        }

        LocalDate today = LocalDate.now(ZONE);
        for (FollowUpState state : followUpStateRepository.findByNextFollowUpDateLessThanEqualAndFollowUpOwnerIsNotNull(today)) {
            if (state.isSnoozed(today)) {
                continue;
            }
            BidLot lot = state.getId().getEntityType() == EntityType.LOT ? bidLotRepository.findById(state.getId().getEntityId()).orElse(null) : null;
            if (lot == null || lot.getBid() == null) {
                continue;
            }
            buckets.computeIfAbsent(state.getFollowUpOwner().getId(), k -> Bucket.empty()).dueFollowUps().add(itemFor(lot));
        }

        int sent = 0;
        for (Map.Entry<UUID, Bucket> entry : buckets.entrySet()) {
            AppUser user = appUserRepository.findById(entry.getKey()).orElse(null);
            if (user == null) {
                continue;
            }
            Bucket bucket = entry.getValue();
            boolean isDirector = directors.stream().anyMatch(d -> d.getId().equals(user.getId()));
            EmailContent content = EmailTemplates.userDailyDigest(user.getFullName(), bucket.dueFollowUps(), bucket.newlyAmberRed(), bucket.stale(),
                    isDirector ? criticalCount : null, appBaseUrl);
            notificationService.sendIfNotAlreadySent(ReminderType.USER_DAILY_DIGEST, EntityType.BID, user.getId(), user, content);
            sent++;
        }
        log.info("Daily per-user digest complete: {} digest(s) sent, {} item(s) critical", sent, criticalCount);
    }

    private AlertItemDto itemFor(BidLot lot) {
        return new AlertItemDto(lot.getBid().getTitle(),
                (lot.getBid().getOrganization() == null ? null : lot.getBid().getOrganization().getName()) + " - " + lot.getLotLabel(),
                "/bids/" + lot.getBid().getId(), null);
    }

    private Set<AppUser> officersForLot(BidLot lot) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (LotAccountOfficer officer : lotAccountOfficerRepository.findById_LotId(lot.getId())) {
            appUserRepository.findById(officer.getId().getUserId()).ifPresent(recipients::add);
        }
        return recipients;
    }

    private Set<AppUser> managersForLot(BidLot lot) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        if (lot.getDivision() != null) {
            for (UserDivision userDivision : userDivisionRepository.findById_DivisionId(lot.getDivision().getId())) {
                appUserRepository.findById(userDivision.getId().getUserId())
                        .filter(u -> u.getRole() == Role.DIVISION_MANAGER)
                        .ifPresent(recipients::add);
            }
        }
        return recipients;
    }
}
