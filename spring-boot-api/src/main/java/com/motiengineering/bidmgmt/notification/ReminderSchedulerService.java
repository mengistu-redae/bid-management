package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.ActivityLogEntry;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidStatusHistory;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.ActivityDigestRowDto;
import com.motiengineering.bidmgmt.dto.StatusChangeDigestRowDto;
import com.motiengineering.bidmgmt.repository.ActivityLogRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.service.DashboardService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One cron job (see bidmgmt.notifications.reminder-cron) that walks every
 * open bid, lot and deal registration once a day looking for exact-day-match
 * due reminders (7/3/1 days before closing, 2 days before clarification,
 * 30/7 days before deal registration expiry, 30 days after opening with the
 * bid bond still outstanding), plus the Director's daily digest. Dedup
 * against resending the same reminder twice in a day is handled entirely by
 * NotificationService/notification_log - this class only decides what is
 * due today.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final BidRepository bidRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final AppUserRepository appUserRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BidStatusHistoryRepository bidStatusHistoryRepository;
    private final DashboardService dashboardService;
    private final NotificationService notificationService;

    @Value("${bidmgmt.notifications.director-email}")
    private String directorEmail;

    @Value("${bidmgmt.notifications.app-base-url}")
    private String appBaseUrl;

    @Scheduled(cron = "${bidmgmt.notifications.reminder-cron}")
    public void run() {
        log.info("Running daily reminder/digest job");
        sendBidClosingAndClarificationReminders();
        sendDealRegistrationExpiryReminders();
        sendBidBondNotReturnedReminders();
        sendDirectorDigest();
    }

    private void sendBidClosingAndClarificationReminders() {
        LocalDate today = LocalDate.now(ZONE);
        for (Bid bid : bidRepository.findAll()) {
            if (bid.getStatus().isTerminal()) {
                continue;
            }
            if (bid.getClosingAt() != null) {
                int daysOut = daysBetween(today, bid.getClosingAt());
                ReminderType type = switch (daysOut) {
                    case 7 -> ReminderType.BID_CLOSING_7D;
                    case 3 -> ReminderType.BID_CLOSING_3D;
                    case 1 -> ReminderType.BID_CLOSING_1D;
                    default -> null;
                };
                if (type != null) {
                    EmailContent content = EmailTemplates.bidClosingReminder(bid, daysOut, appBaseUrl);
                    for (AppUser recipient : recipientsForBid(bid)) {
                        notificationService.sendIfNotAlreadySent(type, EntityType.BID, bid.getId(), recipient, content);
                    }
                }
            }
            if (bid.getClarificationDeadline() != null && daysBetween(today, bid.getClarificationDeadline()) == 2) {
                EmailContent content = EmailTemplates.clarificationReminder(bid, appBaseUrl);
                for (AppUser recipient : recipientsForBid(bid)) {
                    notificationService.sendIfNotAlreadySent(ReminderType.CLARIFICATION_2D, EntityType.BID, bid.getId(), recipient, content);
                }
            }
        }
    }

    private void sendDealRegistrationExpiryReminders() {
        LocalDate today = LocalDate.now(ZONE);
        AppUser director = appUserRepository.findByEmailIgnoreCase(directorEmail).orElse(null);
        for (DealRegistration reg : dealRegistrationRepository.findAll()) {
            if (reg.getExpiryDate() == null || reg.getStatus() == DealRegistrationStatus.EXPIRED || reg.getStatus() == DealRegistrationStatus.REJECTED) {
                continue;
            }
            long daysOut = ChronoUnit.DAYS.between(today, reg.getExpiryDate());
            ReminderType type = daysOut == 30 ? ReminderType.DEAL_REGISTRATION_EXPIRY_30D
                    : daysOut == 7 ? ReminderType.DEAL_REGISTRATION_EXPIRY_7D : null;
            if (type == null) {
                continue;
            }
            EmailContent content = EmailTemplates.dealRegistrationExpiryReminder(reg, (int) daysOut, appBaseUrl);
            if (director != null) {
                notificationService.sendIfNotAlreadySent(type, EntityType.DEAL_REGISTRATION, reg.getId(), director, content);
            }
            if (reg.getCreatedBy() != null) {
                notificationService.sendIfNotAlreadySent(type, EntityType.DEAL_REGISTRATION, reg.getId(), reg.getCreatedBy(), content);
            }
        }
    }

    private void sendBidBondNotReturnedReminders() {
        LocalDate today = LocalDate.now(ZONE);
        for (Bid bid : bidRepository.findAll()) {
            if (bid.getOpeningAt() == null) {
                continue;
            }
            long daysSinceOpening = ChronoUnit.DAYS.between(bid.getOpeningAt().atZone(ZONE).toLocalDate(), today);
            if (daysSinceOpening != 30) {
                continue;
            }
            for (BidLot lot : bid.getLots()) {
                if (lot.isBidBondReturned() || lot.getBidBondAmount() == null) {
                    continue;
                }
                EmailContent content = EmailTemplates.bidBondNotReturnedReminder(bid, lot, appBaseUrl);
                for (AppUser recipient : recipientsForLot(lot)) {
                    notificationService.sendIfNotAlreadySent(ReminderType.BID_BOND_NOT_RETURNED_30D, EntityType.LOT, lot.getId(), recipient, content);
                }
            }
        }
    }

    private void sendDirectorDigest() {
        AppUser director = appUserRepository.findByEmailIgnoreCase(directorEmail).orElse(null);
        if (director == null) {
            log.warn("No AppUser found for director-email {}, skipping daily digest", directorEmail);
            return;
        }
        Instant startOfYesterday = LocalDate.now(ZONE).minusDays(1).atStartOfDay(ZONE).toInstant();
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();

        List<ActivityDigestRowDto> activity = activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startOfYesterday, startOfToday)
                .stream()
                .map(this::toActivityRow)
                .toList();
        List<StatusChangeDigestRowDto> statusChanges = bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(startOfYesterday, startOfToday)
                .stream()
                .map(this::toStatusChangeRow)
                .toList();

        EmailContent content = EmailTemplates.directorDigest(dashboardService.build(), activity, statusChanges, appBaseUrl);
        notificationService.sendIfNotAlreadySent(ReminderType.DIRECTOR_DIGEST, EntityType.BID, director.getId(), director, content);
    }

    private ActivityDigestRowDto toActivityRow(ActivityLogEntry entry) {
        return new ActivityDigestRowDto(entry.getAuthor() == null ? null : entry.getAuthor().getFullName(), entry.getNote());
    }

    private StatusChangeDigestRowDto toStatusChangeRow(BidStatusHistory history) {
        Bid bid = history.getBid() != null ? history.getBid() : history.getLot().getBid();
        return new StatusChangeDigestRowDto(
                bid.getTitle(),
                history.getFromStatus() == null ? null : history.getFromStatus().name(),
                history.getToStatus().name(),
                history.getChangedBy() == null ? null : history.getChangedBy().getFullName());
    }

    private Set<AppUser> recipientsForBid(Bid bid) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (BidLot lot : bid.getLots()) {
            recipients.addAll(recipientsForLot(lot));
        }
        return recipients;
    }

    private Set<AppUser> recipientsForLot(BidLot lot) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (LotAccountOfficer officer : lotAccountOfficerRepository.findById_LotId(lot.getId())) {
            appUserRepository.findById(officer.getId().getUserId()).ifPresent(recipients::add);
        }
        if (lot.getDivision() != null) {
            for (UserDivision userDivision : userDivisionRepository.findById_DivisionId(lot.getDivision().getId())) {
                appUserRepository.findById(userDivision.getId().getUserId())
                        .filter(u -> u.getRole() == Role.DIVISION_MANAGER)
                        .ifPresent(recipients::add);
            }
        }
        return recipients;
    }

    private int daysBetween(LocalDate today, Instant instant) {
        return (int) ChronoUnit.DAYS.between(today, instant.atZone(ZONE).toLocalDate());
    }
}
