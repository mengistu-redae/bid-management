package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.service.FollowUpService;
import com.motiengineering.bidmgmt.service.IndicatorService;
import com.motiengineering.bidmgmt.service.TargetContext;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs every `bidmgmt.notifications.alert-cron` (default: every 30 minutes)
 * and turns each open BidLot's current indicator/staleness/follow-up-due
 * state into at most one new alert per "episode" (see AlertStateId's
 * javadoc for what an episode is). Scoped to BidLot only for now, matching
 * the same lot-level scope BidStatusService's delay-reason requirement
 * already uses - Contract/Logistics/Opportunity/DealRegistration can be
 * added the same way later without new infrastructure.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelayAlertSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DelayAlertSchedulerService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final BidRepository bidRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final AppUserRepository appUserRepository;
    private final AlertStateRepository alertStateRepository;
    private final IndicatorService indicatorService;
    private final FollowUpService followUpService;
    private final NotificationService notificationService;

    @Value("${bidmgmt.notifications.director-email}")
    private String directorEmail;

    @Value("${bidmgmt.notifications.app-base-url}")
    private String appBaseUrl;

    @Scheduled(cron = "${bidmgmt.notifications.alert-cron}")
    @Transactional
    public void run() {
        int lotsScanned = 0;
        int alertsSent = 0;
        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getStatus() == null || lot.getStatus().isTerminal()) {
                    continue;
                }
                lotsScanned++;
                alertsSent += evaluateLot(bid, lot);
            }
        }
        log.info("Delay-alert scan complete: {} open lots scanned, {} alert(s) sent", lotsScanned, alertsSent);
    }

    private int evaluateLot(Bid bid, BidLot lot) {
        AlertItemDto item = new AlertItemDto(
                bid.getTitle(), (bid.getOrganization() == null ? null : bid.getOrganization().getName()) + " - " + lot.getLotLabel(),
                "/bids/" + bid.getId(), null);

        TargetContext context = new TargetContext(
                lot.getDivision() == null ? null : lot.getDivision().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getSector(),
                lot.getOemBrand(), null);
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("closingAt", bid.getClosingAt());
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.LOT, lot.getId(), "status", lot.getStatus().name(), lot.getCreatedAt(), context, deadlineFields);

        int sent = 0;
        sent += evaluateIndicator(lot, item, result);
        sent += evaluateStale(lot, item, result.stale());
        sent += evaluateFollowUpDue(lot, item);
        return sent;
    }

    private int evaluateIndicator(BidLot lot, AlertItemDto item, IndicatorService.IndicatorResult result) {
        LocalDate today = LocalDate.now(ZONE);
        if (followUpService.isSnoozed(EntityType.LOT, lot.getId(), today)) {
            return 0;
        }
        IndicatorLevel level = result.level();
        if (level != IndicatorLevel.AMBER && level != IndicatorLevel.RED && level != IndicatorLevel.CRITICAL) {
            alertStateRepository.deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(EntityType.LOT, lot.getId(), AlertKind.INDICATOR);
            return 0;
        }

        AlertStateId key = new AlertStateId(EntityType.LOT, lot.getId(), AlertKind.INDICATOR, lot.getStatus().name());
        AlertState existing = alertStateRepository.findById(key).orElse(null);
        if (existing != null && severityRank(existing.getLastLevel()) >= severityRank(level.name())) {
            return 0;
        }
        alertStateRepository.save(new AlertState(key, level.name(), Instant.now()));

        ReminderType reminderType = level == IndicatorLevel.AMBER ? ReminderType.DELAY_AMBER
                : level == IndicatorLevel.RED ? ReminderType.DELAY_RED : ReminderType.DELAY_CRITICAL;
        EmailContent content = EmailTemplates.delayAlert(level, item, result.percentUsed(), appBaseUrl);

        int sent = 0;
        for (AppUser owner : officersForLot(lot)) {
            if (level != IndicatorLevel.AMBER || owner.isAmberImmediate()) {
                notificationService.sendIfNotAlreadySent(reminderType, EntityType.LOT, lot.getId(), owner, content);
                sent++;
            }
        }
        if (level == IndicatorLevel.RED || level == IndicatorLevel.CRITICAL) {
            for (AppUser manager : managersForLot(lot)) {
                notificationService.sendIfNotAlreadySent(reminderType, EntityType.LOT, lot.getId(), manager, content);
                sent++;
            }
        }
        if (level == IndicatorLevel.CRITICAL) {
            AppUser director = appUserRepository.findByEmailIgnoreCase(directorEmail).orElse(null);
            if (director != null) {
                notificationService.sendIfNotAlreadySent(reminderType, EntityType.LOT, lot.getId(), director, content);
                sent++;
            }
        }
        return sent;
    }

    private int evaluateStale(BidLot lot, AlertItemDto item, boolean stale) {
        if (!stale) {
            alertStateRepository.deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(EntityType.LOT, lot.getId(), AlertKind.STALE);
            return 0;
        }
        AlertStateId key = new AlertStateId(EntityType.LOT, lot.getId(), AlertKind.STALE, "STALE");
        AlertState existing = alertStateRepository.findById(key).orElse(null);
        boolean doubleStale = isDoubleStale(lot);
        String newLevel = doubleStale ? "STALE_DOUBLE" : "STALE";
        if (existing != null && (existing.getLastLevel().equals(newLevel) || existing.getLastLevel().equals("STALE_DOUBLE"))) {
            return 0; // already notified at this level, or already escalated past it
        }
        alertStateRepository.save(new AlertState(key, newLevel, Instant.now()));

        EmailContent content = EmailTemplates.staleAlert(item, doubleStale, appBaseUrl);
        int sent = 0;
        for (AppUser owner : officersForLot(lot)) {
            notificationService.sendIfNotAlreadySent(ReminderType.STALE_RECORD, EntityType.LOT, lot.getId(), owner, content);
            sent++;
        }
        if (doubleStale) {
            for (AppUser manager : managersForLot(lot)) {
                notificationService.sendIfNotAlreadySent(ReminderType.STALE_RECORD, EntityType.LOT, lot.getId(), manager, content);
                sent++;
            }
        }
        return sent;
    }

    /** "Stale for twice the limit" - reuses the same default-stale-after-days the indicator config exposes, doubled. */
    private boolean isDoubleStale(BidLot lot) {
        var config = indicatorService.indicatorConfig();
        double doubleThreshold = config.getDefaultStaleAfterDays().doubleValue() * 2;
        return indicatorService.isStale(EntityType.LOT, lot.getId(), java.math.BigDecimal.valueOf(doubleThreshold));
    }

    private int evaluateFollowUpDue(BidLot lot, AlertItemDto item) {
        FollowUpState state = followUpService.get(EntityType.LOT, lot.getId()).orElse(null);
        if (state == null || state.getNextFollowUpDate() == null || state.getFollowUpOwner() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZONE);
        if (state.getNextFollowUpDate().isAfter(today)) {
            return 0;
        }
        AlertStateId key = new AlertStateId(EntityType.LOT, lot.getId(), AlertKind.FOLLOWUP_DUE, state.getNextFollowUpDate().toString());
        if (alertStateRepository.findById(key).isPresent()) {
            return 0;
        }
        alertStateRepository.save(new AlertState(key, "NOTIFIED", Instant.now()));
        EmailContent content = EmailTemplates.followUpDueAlert(item, state.getNextFollowUpDate(), appBaseUrl);
        notificationService.sendIfNotAlreadySent(ReminderType.FOLLOWUP_DUE, EntityType.LOT, lot.getId(), state.getFollowUpOwner(), content);
        return 1;
    }

    private static int severityRank(String levelName) {
        return switch (levelName) {
            case "CRITICAL" -> 3;
            case "RED" -> 2;
            case "AMBER" -> 1;
            default -> 0;
        };
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
