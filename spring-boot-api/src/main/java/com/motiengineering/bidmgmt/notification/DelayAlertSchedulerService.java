package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every `bidmgmt.notifications.alert-cron` (default: every 30 minutes)
 * and turns each open BidLot/Contract/Logistics/Opportunity/DealRegistration's
 * current indicator/staleness/follow-up-due state into at most one new alert
 * per "episode" (see AlertStateId's javadoc for what an episode is).
 * "Officer tier" recipients vary by entity since only BidLot and Opportunity
 * have a real assigned-user concept of their own (LotAccountOfficer, owner);
 * Contract/Logistics/DealRegistration instead use createdBy, the same
 * stand-in KeyDateChangeNotificationService already uses for
 * DealRegistration. "Manager tier" (division managers) only applies where a
 * division concept exists (BidLot, Opportunity) - Contract/Logistics/
 * DealRegistration have none, so that tier is simply empty for them, same as
 * everywhere else in this codebase that already made that call.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelayAlertSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DelayAlertSchedulerService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final BidRepository bidRepository;
    private final ContractRepository contractRepository;
    private final LogisticsShipmentRepository logisticsShipmentRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityDivisionRepository opportunityDivisionRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
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
        int entitiesScanned = 0;
        int alertsSent = 0;
        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                if (lot.getStatus() == null || lot.getStatus().isTerminal()) {
                    continue;
                }
                entitiesScanned++;
                alertsSent += evaluateLot(bid, lot);
            }
        }
        for (Contract contract : contractRepository.findAll()) {
            if (contract.getStatus() == null || contract.getStatus().isTerminal()) {
                continue;
            }
            entitiesScanned++;
            alertsSent += evaluateContract(contract);
        }
        for (LogisticsShipment shipment : logisticsShipmentRepository.findAll()) {
            if (shipment.getStatus() == null || shipment.getStatus().isTerminal()) {
                continue;
            }
            entitiesScanned++;
            alertsSent += evaluateLogistics(shipment);
        }
        for (Opportunity opportunity : opportunityRepository.findAll()) {
            if (opportunity.getStage() == null || opportunity.getStage().isTerminal()) {
                continue;
            }
            entitiesScanned++;
            alertsSent += evaluateOpportunity(opportunity);
        }
        for (DealRegistration reg : dealRegistrationRepository.findAll()) {
            if (reg.getStatus() == null || reg.getStatus().isTerminal() || reg.getStatus() == DealRegistrationStatus.APPROVED) {
                continue;
            }
            entitiesScanned++;
            alertsSent += evaluateDealRegistration(reg);
        }
        log.info("Delay-alert scan complete: {} open entities scanned, {} alert(s) sent", entitiesScanned, alertsSent);
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

        Set<AppUser> officers = officersForLot(lot);
        Set<AppUser> managers = managersForDivisions(lot.getDivision() == null ? List.of() : List.of(lot.getDivision().getId()));
        return evaluateAll(EntityType.LOT, lot.getId(), lot.getStatus().name(), item, result, officers, managers);
    }

    private int evaluateContract(Contract contract) {
        Bid bid = contract.getBid();
        AlertItemDto item = new AlertItemDto(
                contract.getContractNumber() != null ? contract.getContractNumber() : "Contract", bid.getTitle(),
                "/contracts/" + contract.getId(), null);
        TargetContext context = new TargetContext(null,
                bid.getOrganization() == null ? null : bid.getOrganization().getSector(), null, null);
        Instant deliveryDeadline = contract.getDeliveryDeadline() == null ? null
                : contract.getDeliveryDeadline().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("deliveryDeadline", deliveryDeadline);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.CONTRACT, contract.getId(), "status", contract.getStatus().name(), contract.getCreatedAt(), context, deadlineFields);

        Set<AppUser> officers = contract.getCreatedBy() == null ? Set.of() : Set.of(contract.getCreatedBy());
        return evaluateAll(EntityType.CONTRACT, contract.getId(), contract.getStatus().name(), item, result, officers, Set.of());
    }

    private int evaluateLogistics(LogisticsShipment shipment) {
        Contract contract = shipment.getContract();
        Bid bid = contract.getBid();
        AlertItemDto item = new AlertItemDto(
                shipment.getShipmentMode().name() + " shipment", bid.getTitle(),
                "/logistics/" + shipment.getId(), null);
        TargetContext context = new TargetContext(null, null, null, shipment.getShipmentMode());
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.LOGISTICS, shipment.getId(), "status", shipment.getStatus().name(), shipment.getCreatedAt(), context, Map.of());

        Set<AppUser> officers = shipment.getCreatedBy() == null ? Set.of() : Set.of(shipment.getCreatedBy());
        return evaluateAll(EntityType.LOGISTICS, shipment.getId(), shipment.getStatus().name(), item, result, officers, Set.of());
    }

    private int evaluateOpportunity(Opportunity opportunity) {
        AlertItemDto item = new AlertItemDto(opportunity.getTitle(),
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getName(),
                "/opportunities/" + opportunity.getId(), null);
        List<UUID> divisionIds = opportunityDivisionRepository.findById_OpportunityId(opportunity.getId()).stream()
                .map(od -> od.getId().getDivisionId())
                .toList();
        TargetContext context = new TargetContext(
                divisionIds.size() == 1 ? divisionIds.get(0) : null,
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.OPPORTUNITY, opportunity.getId(), "stage", opportunity.getStage().name(), opportunity.getCreatedAt(), context, Map.of());

        Set<AppUser> officers = opportunity.getOwner() == null ? Set.of() : Set.of(opportunity.getOwner());
        Set<AppUser> managers = managersForDivisions(divisionIds);
        return evaluateAll(EntityType.OPPORTUNITY, opportunity.getId(), opportunity.getStage().name(), item, result, officers, managers);
    }

    private int evaluateDealRegistration(DealRegistration reg) {
        AlertItemDto item = new AlertItemDto(reg.getOem().getName() + " - " + reg.getOrganization().getName(),
                reg.getRegistrationId(), "/deal-registrations/" + reg.getId(), null);
        TargetContext context = new TargetContext(null, reg.getOrganization() == null ? null : reg.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.DEAL_REGISTRATION, reg.getId(), "status", reg.getStatus().name(), reg.getCreatedAt(), context, Map.of());

        Set<AppUser> officers = reg.getCreatedBy() == null ? Set.of() : Set.of(reg.getCreatedBy());
        return evaluateAll(EntityType.DEAL_REGISTRATION, reg.getId(), reg.getStatus().name(), item, result, officers, Set.of());
    }

    private int evaluateAll(EntityType entityType, UUID entityId, String currentStageValue, AlertItemDto item,
                             IndicatorService.IndicatorResult result, Set<AppUser> officers, Set<AppUser> managers) {
        int sent = 0;
        sent += evaluateIndicator(entityType, entityId, currentStageValue, item, result, officers, managers);
        sent += evaluateStale(entityType, entityId, item, result.stale(), officers, managers);
        sent += evaluateFollowUpDue(entityType, entityId, item);
        return sent;
    }

    private int evaluateIndicator(EntityType entityType, UUID entityId, String currentStageValue, AlertItemDto item,
                                   IndicatorService.IndicatorResult result, Set<AppUser> officers, Set<AppUser> managers) {
        LocalDate today = LocalDate.now(ZONE);
        if (followUpService.isSnoozed(entityType, entityId, today)) {
            return 0;
        }
        IndicatorLevel level = result.level();
        if (level != IndicatorLevel.AMBER && level != IndicatorLevel.RED && level != IndicatorLevel.CRITICAL) {
            alertStateRepository.deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(entityType, entityId, AlertKind.INDICATOR);
            return 0;
        }

        AlertStateId key = new AlertStateId(entityType, entityId, AlertKind.INDICATOR, currentStageValue);
        AlertState existing = alertStateRepository.findById(key).orElse(null);
        if (existing != null && severityRank(existing.getLastLevel()) >= severityRank(level.name())) {
            return 0;
        }
        alertStateRepository.save(new AlertState(key, level.name(), Instant.now()));

        ReminderType reminderType = level == IndicatorLevel.AMBER ? ReminderType.DELAY_AMBER
                : level == IndicatorLevel.RED ? ReminderType.DELAY_RED : ReminderType.DELAY_CRITICAL;
        EmailContent content = EmailTemplates.delayAlert(level, item, result.percentUsed(), appBaseUrl);

        int sent = 0;
        for (AppUser owner : officers) {
            if (level != IndicatorLevel.AMBER || owner.isAmberImmediate()) {
                notificationService.sendIfNotAlreadySent(reminderType, entityType, entityId, owner, content);
                sent++;
            }
        }
        if (level == IndicatorLevel.RED || level == IndicatorLevel.CRITICAL) {
            for (AppUser manager : managers) {
                notificationService.sendIfNotAlreadySent(reminderType, entityType, entityId, manager, content);
                sent++;
            }
        }
        if (level == IndicatorLevel.CRITICAL) {
            AppUser director = appUserRepository.findByEmailIgnoreCase(directorEmail).orElse(null);
            if (director != null) {
                notificationService.sendIfNotAlreadySent(reminderType, entityType, entityId, director, content);
                sent++;
            }
        }
        return sent;
    }

    private int evaluateStale(EntityType entityType, UUID entityId, AlertItemDto item, boolean stale, Set<AppUser> officers, Set<AppUser> managers) {
        if (!stale) {
            alertStateRepository.deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(entityType, entityId, AlertKind.STALE);
            return 0;
        }
        AlertStateId key = new AlertStateId(entityType, entityId, AlertKind.STALE, "STALE");
        AlertState existing = alertStateRepository.findById(key).orElse(null);
        boolean doubleStale = isDoubleStale(entityType, entityId);
        String newLevel = doubleStale ? "STALE_DOUBLE" : "STALE";
        if (existing != null && (existing.getLastLevel().equals(newLevel) || existing.getLastLevel().equals("STALE_DOUBLE"))) {
            return 0; // already notified at this level, or already escalated past it
        }
        alertStateRepository.save(new AlertState(key, newLevel, Instant.now()));

        EmailContent content = EmailTemplates.staleAlert(item, doubleStale, appBaseUrl);
        int sent = 0;
        for (AppUser owner : officers) {
            notificationService.sendIfNotAlreadySent(ReminderType.STALE_RECORD, entityType, entityId, owner, content);
            sent++;
        }
        if (doubleStale) {
            for (AppUser manager : managers) {
                notificationService.sendIfNotAlreadySent(ReminderType.STALE_RECORD, entityType, entityId, manager, content);
                sent++;
            }
        }
        return sent;
    }

    /** "Stale for twice the limit" - reuses the same default-stale-after-days the indicator config exposes, doubled. */
    private boolean isDoubleStale(EntityType entityType, UUID entityId) {
        var config = indicatorService.indicatorConfig();
        double doubleThreshold = config.getDefaultStaleAfterDays().doubleValue() * 2;
        return indicatorService.isStale(entityType, entityId, java.math.BigDecimal.valueOf(doubleThreshold));
    }

    private int evaluateFollowUpDue(EntityType entityType, UUID entityId, AlertItemDto item) {
        FollowUpState state = followUpService.get(entityType, entityId).orElse(null);
        if (state == null || state.getNextFollowUpDate() == null || state.getFollowUpOwner() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZONE);
        if (state.getNextFollowUpDate().isAfter(today)) {
            return 0;
        }
        AlertStateId key = new AlertStateId(entityType, entityId, AlertKind.FOLLOWUP_DUE, state.getNextFollowUpDate().toString());
        if (alertStateRepository.findById(key).isPresent()) {
            return 0;
        }
        alertStateRepository.save(new AlertState(key, "NOTIFIED", Instant.now()));
        EmailContent content = EmailTemplates.followUpDueAlert(item, state.getNextFollowUpDate(), appBaseUrl);
        notificationService.sendIfNotAlreadySent(ReminderType.FOLLOWUP_DUE, entityType, entityId, state.getFollowUpOwner(), content);
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

    private Set<AppUser> managersForDivisions(List<UUID> divisionIds) {
        Set<AppUser> recipients = new LinkedHashSet<>();
        for (UUID divisionId : divisionIds) {
            for (UserDivision userDivision : userDivisionRepository.findById_DivisionId(divisionId)) {
                appUserRepository.findById(userDivision.getId().getUserId())
                        .filter(u -> u.getRole() == Role.DIVISION_MANAGER)
                        .ifPresent(recipients::add);
            }
        }
        return recipients;
    }
}
