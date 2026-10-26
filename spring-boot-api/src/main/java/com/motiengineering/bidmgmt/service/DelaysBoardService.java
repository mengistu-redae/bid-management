package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.WaitingOnState;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.DelayBoardRowDto;
import com.motiengineering.bidmgmt.dto.DelaySummaryDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.ContractRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.LogisticsShipmentRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every currently amber/red/critical open Bid lot, Contract, Logistics
 * shipment, Opportunity or Deal Registration, for managers/the Director to
 * triage - a live, filterable superset of the dashboard's "Delays" section
 * (which only shows the top 10, and shares this same scan via summary()).
 * Contract/Logistics/Opportunity/DealRegistration rows carry no account
 * officers (no equivalent of lot_account_officers exists for them yet), so
 * that field is simply empty on those rows rather than guessed; Contract/
 * Logistics/DealRegistration additionally carry no division (that concept
 * lives on BidLot and, for Opportunity, its own division links).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelaysBoardService {

    private final BidRepository bidRepository;
    private final ContractRepository contractRepository;
    private final LogisticsShipmentRepository logisticsShipmentRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityDivisionRepository opportunityDivisionRepository;
    private final DealRegistrationRepository dealRegistrationRepository;
    private final DivisionRepository divisionRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final AppUserRepository appUserRepository;
    private final IndicatorService indicatorService;
    private final WaitingOnService waitingOnService;
    private final FollowUpService followUpService;

    public record Filter(UUID divisionId, UUID officerId, UUID organizationId, String oemBrand, String waitingOnParty, String level) {
        public static final Filter EMPTY = new Filter(null, null, null, null, null, null);
    }

    public List<DelayBoardRowDto> list(Filter filter) {
        return allRows().stream()
                .filter(row -> isShowable(row) && matches(row, filter))
                .sorted(Comparator.comparing((DelayBoardRowDto r) -> r.percentUsed() == null ? 0.0 : r.percentUsed()).reversed())
                .toList();
    }

    /**
     * Same green/amber/red/critical/stale counts and top-delayed list the dashboard has always shown,
     * now sourced from every BidLot, Contract and Logistics row instead of BidLot alone. divisionId
     * scopes to one division (BidLot rows only - Contract/Logistics carry no division); noDivisionOnly
     * scopes to BidLot rows with no division plus every Contract/Logistics row (neither has a division
     * to exclude it); both null/false means no scoping at all.
     */
    public DelaySummaryDto summary(UUID divisionId, boolean noDivisionOnly, int topLimit) {
        List<DelayBoardRowDto> scoped = allRows().stream()
                .filter(row -> noDivisionOnly ? row.divisionId() == null : (divisionId == null || divisionId.equals(row.divisionId())))
                .toList();

        int green = 0;
        int amber = 0;
        int red = 0;
        int critical = 0;
        int stale = 0;
        for (DelayBoardRowDto row : scoped) {
            if (row.stale()) {
                stale++;
            }
            switch (row.indicatorLevel()) {
                case "GREEN" -> green++;
                case "AMBER" -> amber++;
                case "RED" -> red++;
                case "CRITICAL" -> critical++;
                default -> { }
            }
        }
        List<DelayBoardRowDto> top = scoped.stream()
                .filter(row -> "AMBER".equals(row.indicatorLevel()) || "RED".equals(row.indicatorLevel()) || "CRITICAL".equals(row.indicatorLevel()))
                .sorted(Comparator.comparing((DelayBoardRowDto r) -> r.percentUsed() == null ? 0.0 : r.percentUsed()).reversed())
                .limit(topLimit)
                .toList();
        return new DelaySummaryDto(green, amber, red, critical, stale, top);
    }

    private List<DelayBoardRowDto> allRows() {
        List<DelayBoardRowDto> rows = new ArrayList<>();
        for (Bid bid : bidRepository.findAll()) {
            for (BidLot lot : bid.getLots()) {
                DelayBoardRowDto row = computeRow(bid, lot);
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        for (Contract contract : contractRepository.findAll()) {
            DelayBoardRowDto row = computeContractRow(contract);
            if (row != null) {
                rows.add(row);
            }
        }
        for (LogisticsShipment shipment : logisticsShipmentRepository.findAll()) {
            DelayBoardRowDto row = computeLogisticsRow(shipment);
            if (row != null) {
                rows.add(row);
            }
        }
        for (Opportunity opportunity : opportunityRepository.findAll()) {
            DelayBoardRowDto row = computeOpportunityRow(opportunity);
            if (row != null) {
                rows.add(row);
            }
        }
        for (DealRegistration reg : dealRegistrationRepository.findAll()) {
            DelayBoardRowDto row = computeDealRegistrationRow(reg);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean isShowable(DelayBoardRowDto row) {
        return "AMBER".equals(row.indicatorLevel()) || "RED".equals(row.indicatorLevel()) || "CRITICAL".equals(row.indicatorLevel()) || row.stale();
    }

    /** Every open lot's row, regardless of indicator level - MyFollowUpsService needs GREEN ones too (to spot "turning amber soon"). */
    public DelayBoardRowDto computeRow(Bid bid, BidLot lot) {
        if (lot.getStatus() == null || lot.getStatus().isTerminal()) {
            return null;
        }
        TargetContext context = new TargetContext(
                lot.getDivision() == null ? null : lot.getDivision().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getSector(),
                lot.getOemBrand(), null);
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("closingAt", bid.getClosingAt());
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.LOT, lot.getId(), "status", lot.getStatus().name(), lot.getCreatedAt(), context, deadlineFields);

        WaitingOnState waitingOn = waitingOnService.get(EntityType.LOT, lot.getId()).orElse(null);
        FollowUpState followUp = followUpService.get(EntityType.LOT, lot.getId()).orElse(null);
        List<String> officerNames = lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                .map((LotAccountOfficer loa) -> appUserRepository.findById(loa.getId().getUserId()).map(com.motiengineering.bidmgmt.domain.AppUser::getFullName).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new DelayBoardRowDto(
                "LOT", lot.getId(), "/bids/" + bid.getId(),
                bid.getId(), bid.getTitle(), lot.getId(), lot.getLotLabel(),
                bid.getOrganization() == null ? null : bid.getOrganization().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getName(),
                lot.getDivision() == null ? null : lot.getDivision().getId(),
                lot.getDivision() == null ? null : lot.getDivision().getName(),
                lot.getStatus().name(), lot.getOemBrand(),
                result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.stale(),
                waitingOn == null || waitingOn.getPartyType() == null ? null : waitingOn.getPartyType().name(),
                officerNames,
                followUp == null ? null : followUp.getNextFollowUpDate(),
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName(),
                bid.getClosingAt());
    }

    /**
     * Mirrors ContractMapper.indicatorFor exactly, so the board and the contract's own badge never disagree.
     * Public (like computeRow) so MyFollowUpsService can reuse it for the follow-up-owner's own view.
     */
    public DelayBoardRowDto computeContractRow(Contract contract) {
        if (contract.getStatus() == null || contract.getStatus().isTerminal()) {
            return null;
        }
        Bid bid = contract.getBid();
        TargetContext context = new TargetContext(null,
                bid.getOrganization() == null ? null : bid.getOrganization().getSector(), null, null);
        Instant deliveryDeadline = contract.getDeliveryDeadline() == null ? null
                : contract.getDeliveryDeadline().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Map<String, Instant> deadlineFields = IndicatorService.deadlineField("deliveryDeadline", deliveryDeadline);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.CONTRACT, contract.getId(), "status", contract.getStatus().name(), contract.getCreatedAt(), context, deadlineFields);

        WaitingOnState waitingOn = waitingOnService.get(EntityType.CONTRACT, contract.getId()).orElse(null);
        FollowUpState followUp = followUpService.get(EntityType.CONTRACT, contract.getId()).orElse(null);

        return new DelayBoardRowDto(
                "CONTRACT", contract.getId(), "/contracts/" + contract.getId(),
                null, contract.getContractNumber() != null ? contract.getContractNumber() : "Contract", null, bid.getTitle(),
                bid.getOrganization() == null ? null : bid.getOrganization().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getName(),
                null, null,
                contract.getStatus().name(), null,
                result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.stale(),
                waitingOn == null || waitingOn.getPartyType() == null ? null : waitingOn.getPartyType().name(),
                List.of(),
                followUp == null ? null : followUp.getNextFollowUpDate(),
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName(),
                null);
    }

    /**
     * Mirrors LogisticsMapper.indicatorFor exactly, so the board and the shipment's own badge never disagree.
     * Public (like computeRow) so MyFollowUpsService can reuse it for the follow-up-owner's own view.
     */
    public DelayBoardRowDto computeLogisticsRow(LogisticsShipment shipment) {
        if (shipment.getStatus() == null || shipment.getStatus().isTerminal()) {
            return null;
        }
        Contract contract = shipment.getContract();
        Bid bid = contract.getBid();
        TargetContext context = new TargetContext(null, null, null, shipment.getShipmentMode());
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.LOGISTICS, shipment.getId(), "status", shipment.getStatus().name(), shipment.getCreatedAt(), context, Map.of());

        WaitingOnState waitingOn = waitingOnService.get(EntityType.LOGISTICS, shipment.getId()).orElse(null);
        FollowUpState followUp = followUpService.get(EntityType.LOGISTICS, shipment.getId()).orElse(null);

        return new DelayBoardRowDto(
                "LOGISTICS", shipment.getId(), "/logistics/" + shipment.getId(),
                null, shipment.getShipmentMode().name() + " shipment", null, bid.getTitle(),
                bid.getOrganization() == null ? null : bid.getOrganization().getId(),
                bid.getOrganization() == null ? null : bid.getOrganization().getName(),
                null, null,
                shipment.getStatus().name(), null,
                result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.stale(),
                waitingOn == null || waitingOn.getPartyType() == null ? null : waitingOn.getPartyType().name(),
                List.of(),
                followUp == null ? null : followUp.getNextFollowUpDate(),
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName(),
                null);
    }

    /** Mirrors OpportunityMapper.indicatorFor exactly, so the board and the opportunity's own badge can never disagree. */
    public DelayBoardRowDto computeOpportunityRow(Opportunity opportunity) {
        if (opportunity.getStage() == null || opportunity.getStage().isTerminal()) {
            return null;
        }
        List<UUID> divisionIds = opportunityDivisionRepository.findById_OpportunityId(opportunity.getId()).stream()
                .map(od -> od.getId().getDivisionId())
                .toList();
        UUID divisionId = divisionIds.size() == 1 ? divisionIds.get(0) : null;
        String divisionName = divisionId == null ? null : divisionRepository.findById(divisionId).map(com.motiengineering.bidmgmt.domain.Division::getName).orElse(null);
        TargetContext context = new TargetContext(divisionId,
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.OPPORTUNITY, opportunity.getId(), "stage", opportunity.getStage().name(), opportunity.getCreatedAt(), context, Map.of());

        WaitingOnState waitingOn = waitingOnService.get(EntityType.OPPORTUNITY, opportunity.getId()).orElse(null);
        FollowUpState followUp = followUpService.get(EntityType.OPPORTUNITY, opportunity.getId()).orElse(null);

        return new DelayBoardRowDto(
                "OPPORTUNITY", opportunity.getId(), "/opportunities/" + opportunity.getId(),
                null, opportunity.getTitle(), null, null,
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getId(),
                opportunity.getOrganization() == null ? null : opportunity.getOrganization().getName(),
                divisionId, divisionName,
                opportunity.getStage().name(), null,
                result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.stale(),
                waitingOn == null || waitingOn.getPartyType() == null ? null : waitingOn.getPartyType().name(),
                List.of(),
                followUp == null ? null : followUp.getNextFollowUpDate(),
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName(),
                null);
    }

    /**
     * Mirrors DealRegistrationMapper.indicatorFor exactly, so the board and the registration's own
     * badge can never disagree - APPROVED is excluded the same way the mapper excludes it (not
     * technically terminal, since it can still move to EXPIRED, but no longer "in progress").
     */
    public DelayBoardRowDto computeDealRegistrationRow(DealRegistration reg) {
        var status = reg.getStatus();
        if (status == null || status.isTerminal() || status == com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus.APPROVED) {
            return null;
        }
        TargetContext context = new TargetContext(null, reg.getOrganization() == null ? null : reg.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.DEAL_REGISTRATION, reg.getId(), "status", status.name(), reg.getCreatedAt(), context, Map.of());

        WaitingOnState waitingOn = waitingOnService.get(EntityType.DEAL_REGISTRATION, reg.getId()).orElse(null);
        FollowUpState followUp = followUpService.get(EntityType.DEAL_REGISTRATION, reg.getId()).orElse(null);

        return new DelayBoardRowDto(
                "DEAL_REGISTRATION", reg.getId(), "/deal-registrations/" + reg.getId(),
                null, reg.getOem().getName(), null, reg.getRegistrationId(),
                reg.getOrganization() == null ? null : reg.getOrganization().getId(),
                reg.getOrganization() == null ? null : reg.getOrganization().getName(),
                null, null,
                status.name(), reg.getOem().getName(),
                result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.stale(),
                waitingOn == null || waitingOn.getPartyType() == null ? null : waitingOn.getPartyType().name(),
                List.of(),
                followUp == null ? null : followUp.getNextFollowUpDate(),
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName(),
                null);
    }

    private boolean matches(DelayBoardRowDto row, Filter filter) {
        if (filter.divisionId() != null && !filter.divisionId().equals(row.divisionId())) {
            return false;
        }
        if (filter.organizationId() != null && !filter.organizationId().equals(row.organizationId())) {
            return false;
        }
        if (filter.oemBrand() != null && !filter.oemBrand().isBlank() && (row.oemBrand() == null || !row.oemBrand().equalsIgnoreCase(filter.oemBrand()))) {
            return false;
        }
        if (filter.waitingOnParty() != null && !filter.waitingOnParty().isBlank() && !filter.waitingOnParty().equalsIgnoreCase(row.waitingOnParty())) {
            return false;
        }
        if (filter.level() != null && !filter.level().isBlank() && !filter.level().equalsIgnoreCase(row.indicatorLevel()) && !(filter.level().equalsIgnoreCase("STALE") && row.stale())) {
            return false;
        }
        if (filter.officerId() != null) {
            boolean hasOfficer = row.lotId() != null && lotAccountOfficerRepository.findById_LotId(row.lotId()).stream()
                    .anyMatch(loa -> loa.getId().getUserId().equals(filter.officerId()));
            if (!hasOfficer) {
                return false;
            }
        }
        return true;
    }
}
