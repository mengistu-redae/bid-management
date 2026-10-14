package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.WaitingOnState;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.DelayBoardRowDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All currently amber/red/critical open lots, for managers/the Director to
 * triage - a live, filterable superset of the dashboard's "Delays"
 * section (which only shows the top 10). Scoped to BidLot, same as the
 * rest of this phase's alerting.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelaysBoardService {

    private final BidRepository bidRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final AppUserRepository appUserRepository;
    private final IndicatorService indicatorService;
    private final WaitingOnService waitingOnService;
    private final FollowUpService followUpService;

    public record Filter(UUID divisionId, UUID officerId, UUID organizationId, String oemBrand, String waitingOnParty, String level) {
        public static final Filter EMPTY = new Filter(null, null, null, null, null, null);
    }

    public List<DelayBoardRowDto> list(Filter filter) {
        return bidRepository.findAll().stream()
                .flatMap(bid -> bid.getLots().stream().map(lot -> computeRow(bid, lot)))
                .filter(java.util.Objects::nonNull)
                .filter(row -> isShowable(row) && matches(row, filter))
                .sorted(Comparator.comparing((DelayBoardRowDto r) -> r.percentUsed() == null ? 0.0 : r.percentUsed()).reversed())
                .toList();
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
                followUp == null || followUp.getFollowUpOwner() == null ? null : followUp.getFollowUpOwner().getFullName());
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
            boolean hasOfficer = lotAccountOfficerRepository.findById_LotId(row.lotId()).stream()
                    .anyMatch(loa -> loa.getId().getUserId().equals(filter.officerId()));
            if (!hasOfficer) {
                return false;
            }
        }
        return true;
    }
}
