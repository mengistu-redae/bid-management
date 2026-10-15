package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.service.DelayAnalyticsService;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs on the same cadence as DelayAlertSchedulerService
 * (bidmgmt.notifications.alert-cron, every 30 min by default) and turns
 * each new audit_log row on a curated "key date" field
 * (DelayAnalyticsService.KEY_DATE_FIELDS - the exact same list the
 * date-slippage report uses) into one KEY_DATE_CHANGED email.
 * alert_state (contextKey = the audit_log row's own id) makes sure a given
 * change is only ever notified once, no matter how many times this scan
 * re-runs - audit_log rows are permanent and never disappear, so without
 * this the same historical change would re-fire on every later day's scan
 * (NotificationService's own dedup is "once per calendar day", not "once
 * ever"). Scoped to Bid/Opportunity/DealRegistration - the only entity
 * types KEY_DATE_FIELDS currently covers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeyDateChangeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(KeyDateChangeNotificationService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final AuditLogRepository auditLogRepository;
    private final AlertStateRepository alertStateRepository;
    private final BidRepository bidRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityDivisionRepository opportunityDivisionRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;

    @Value("${bidmgmt.notifications.director-email}")
    private String directorEmail;

    @Value("${bidmgmt.notifications.app-base-url}")
    private String appBaseUrl;

    @Scheduled(cron = "${bidmgmt.notifications.alert-cron}")
    @Transactional
    public void run() {
        int changesFound = 0;
        int notificationsSent = 0;
        for (AuditLogEntry entry : auditLogRepository.findAll()) {
            List<String> keyDateFields = DelayAnalyticsService.KEY_DATE_FIELDS.get(entry.getEntityType());
            if (keyDateFields == null || !keyDateFields.contains(entry.getFieldName())) {
                continue;
            }
            AlertStateId key = new AlertStateId(EntityType.valueOf(entry.getEntityType()), entry.getEntityId(), AlertKind.KEY_DATE_CHANGED, entry.getId().toString());
            if (alertStateRepository.existsById(key)) {
                continue;
            }
            changesFound++;
            notificationsSent += notify(entry);
            alertStateRepository.save(new AlertState(key, "NOTIFIED", Instant.now()));
        }
        log.info("Key-date-change scan complete: {} new change(s) found, {} notification(s) sent", changesFound, notificationsSent);
    }

    private int notify(AuditLogEntry entry) {
        return switch (entry.getEntityType()) {
            case "BID" -> notifyForBid(entry);
            case "OPPORTUNITY" -> notifyForOpportunity(entry);
            case "DEAL_REGISTRATION" -> notifyForDealRegistration(entry);
            default -> 0;
        };
    }

    private int notifyForBid(AuditLogEntry entry) {
        Bid bid = bidRepository.findById(entry.getEntityId()).orElse(null);
        if (bid == null) {
            return 0;
        }
        AlertItemDto item = new AlertItemDto(bid.getTitle(),
                bid.getOrganization() == null ? null : bid.getOrganization().getName(),
                "/bids/" + bid.getId(), null);
        EmailContent content = content(entry, item);
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (BidLot lot : bid.getLots()) {
            recipients.addAll(recipientsForLot(lot));
        }
        return send(entry, content, recipients);
    }

    private int notifyForOpportunity(AuditLogEntry entry) {
        Opportunity opportunity = opportunityRepository.findById(entry.getEntityId()).orElse(null);
        if (opportunity == null) {
            return 0;
        }
        AlertItemDto item = new AlertItemDto(opportunity.getTitle(),
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getName(),
                "/opportunities/" + opportunity.getId(), null);
        EmailContent content = content(entry, item);
        Set<AppUser> recipients = new LinkedHashSet<>();
        if (opportunity.getOwner() != null) {
            recipients.add(opportunity.getOwner());
        }
        for (var opportunityDivision : opportunityDivisionRepository.findById_OpportunityId(opportunity.getId())) {
            for (UserDivision userDivision : userDivisionRepository.findById_DivisionId(opportunityDivision.getId().getDivisionId())) {
                appUserRepository.findById(userDivision.getId().getUserId())
                        .filter(u -> u.getRole() == Role.DIVISION_MANAGER)
                        .ifPresent(recipients::add);
            }
        }
        return send(entry, content, recipients);
    }

    private int notifyForDealRegistration(AuditLogEntry entry) {
        DealRegistration reg = dealRegistrationRepository.findById(entry.getEntityId()).orElse(null);
        if (reg == null) {
            return 0;
        }
        AlertItemDto item = new AlertItemDto(reg.getOem().getName() + " - " + reg.getOrganization().getName(),
                reg.getRegistrationId(), "/deal-registrations/" + reg.getId(), null);
        EmailContent content = content(entry, item);
        // Same recipient pair as ReminderSchedulerService's expiry reminders - DealRegistration
        // has neither an owner nor a division, so createdBy + the Director is the only
        // available audience.
        Set<AppUser> recipients = new LinkedHashSet<>();
        if (reg.getCreatedBy() != null) {
            recipients.add(reg.getCreatedBy());
        }
        appUserRepository.findByEmailIgnoreCase(directorEmail).ifPresent(recipients::add);
        return send(entry, content, recipients);
    }

    private EmailContent content(AuditLogEntry entry, AlertItemDto item) {
        return EmailTemplates.keyDateChangedAlert(item, entry.getFieldName(), formatValue(entry.getOldValue()), formatValue(entry.getNewValue()), appBaseUrl);
    }

    private int send(AuditLogEntry entry, EmailContent content, Set<AppUser> recipients) {
        int sent = 0;
        for (AppUser recipient : recipients) {
            notificationService.sendIfNotAlreadySent(ReminderType.KEY_DATE_CHANGED, EntityType.valueOf(entry.getEntityType()), entry.getEntityId(), recipient, content);
            sent++;
        }
        return sent;
    }

    private Set<AppUser> recipientsForLot(BidLot lot) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (var officer : lotAccountOfficerRepository.findById_LotId(lot.getId())) {
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

    /** oldValue/newValue are the raw Object.toString() AuditService stored - an ISO instant for Bid's fields, a LocalDate string for everything else. */
    private String formatValue(String raw) {
        if (raw == null) {
            return "(not set)";
        }
        try {
            return DATETIME_FMT.withZone(ZONE).format(Instant.parse(raw));
        } catch (DateTimeParseException e) {
            // Fall through to LocalDate.
        }
        try {
            return DATE_FMT.format(LocalDate.parse(raw));
        } catch (DateTimeParseException e) {
            return raw;
        }
    }
}
