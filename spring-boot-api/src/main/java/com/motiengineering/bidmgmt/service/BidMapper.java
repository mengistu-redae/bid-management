package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidScout;
import com.motiengineering.bidmgmt.domain.ChecklistItem;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotScopeType;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.dto.BidDto;
import com.motiengineering.bidmgmt.dto.ChecklistItemDto;
import com.motiengineering.bidmgmt.dto.DivisionDto;
import com.motiengineering.bidmgmt.dto.LotDto;
import com.motiengineering.bidmgmt.dto.OrganizationDto;
import com.motiengineering.bidmgmt.dto.UserDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidScoutRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BidMapper {

    private final BidScoutRepository bidScoutRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final LotScopeTypeRepository lotScopeTypeRepository;
    private final AppUserRepository appUserRepository;
    private final ChecklistService checklistService;
    private final BidAccessService bidAccessService;

    public BidDto toDto(Bid bid) {
        List<UUID> scoutUserIds = bidScoutRepository.findById_BidId(bid.getId()).stream()
                .map(bs -> bs.getId().getUserId())
                .toList();
        Map<UUID, AppUser> usersById = appUserRepository.findAllById(scoutUserIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        List<UserDto> scouts = scoutUserIds.stream().map(usersById::get).filter(u -> u != null).map(this::toUserDto).toList();

        List<LotDto> lots = bid.getLots().stream().map(this::toDto).toList();

        return new BidDto(
                bid.getId(),
                toDto(bid.getOrganization()),
                bid.getTitle(),
                bid.getReferenceNumber(),
                bid.getSource() == null ? null : bid.getSource().name(),
                bid.getScoutedDate(),
                scouts,
                bid.getClosingAt(),
                bid.getOpeningAt(),
                bid.getClarificationDeadline(),
                bid.getBidValidityDays(),
                bid.getStatus() == null ? null : bid.getStatus().name(),
                bid.getGoNoGoDecision() == null ? null : bid.getGoNoGoDecision().name(),
                bid.getGoNoGoReason(),
                bid.getExitReason(),
                bid.getNotes(),
                lots,
                bidAccessService.canEdit(bid),
                bid.getCreatedAt(),
                bid.getUpdatedAt());
    }

    public LotDto toDto(BidLot lot) {
        List<UUID> officerIds = lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                .map((LotAccountOfficer loa) -> loa.getId().getUserId())
                .toList();
        List<UserDto> officers = appUserRepository.findAllById(officerIds).stream().map(this::toUserDto).toList();

        List<String> scopeTypes = lotScopeTypeRepository.findById_LotId(lot.getId()).stream()
                .map((LotScopeType lst) -> lst.getId().getScopeType().name())
                .toList();

        List<ChecklistItemDto> items = checklistService.forLot(lot.getId()).stream().map(this::toDto).toList();
        int progress = checklistService.progressPercent(lot.getId());

        return new LotDto(
                lot.getId(),
                lot.getBid().getId(),
                lot.getLotLabel(),
                lot.getDescription(),
                toDto(lot.getDivision()),
                lot.getEstimatedValue(),
                lot.getEstimatedValueCurrency() == null ? null : lot.getEstimatedValueCurrency().name(),
                lot.getBidBondAmount(),
                lot.getBidBondCurrency() == null ? null : lot.getBidBondCurrency().name(),
                lot.getBidBondValidityDays(),
                lot.getBidBondForm() == null ? null : lot.getBidBondForm().name(),
                lot.isBidBondReturned(),
                lot.getBidBondReturnedAt(),
                lot.getBidBondIssuingBank(),
                lot.getBidBondIssueDate(),
                lot.getOemBrand(),
                lot.getStatus() == null ? null : lot.getStatus().name(),
                lot.getExitReason(),
                lot.getOutcome() == null ? null : lot.getOutcome().name(),
                lot.getWinnerName(),
                lot.getWinningPrice(),
                lot.getWinningPriceCurrency() == null ? null : lot.getWinningPriceCurrency().name(),
                lot.isNeedsReview(),
                lot.getReviewNote(),
                scopeTypes,
                officers,
                items,
                progress);
    }

    public ChecklistItemDto toDto(ChecklistItem item) {
        return new ChecklistItemDto(
                item.getId(),
                item.getTitle(),
                item.getOwner() == null ? null : item.getOwner().getId(),
                item.getOwner() == null ? null : item.getOwner().getFullName(),
                item.getDueDate(),
                item.isDone(),
                item.getDoneAt(),
                item.getDoneBy() == null ? null : item.getDoneBy().getFullName());
    }

    public OrganizationDto toDto(Organization org) {
        if (org == null) {
            return null;
        }
        return new OrganizationDto(org.getId(), org.getName(), org.getSector() == null ? null : org.getSector().name(), org.getNotes());
    }

    public DivisionDto toDto(Division division) {
        if (division == null) {
            return null;
        }
        return new DivisionDto(division.getId(), division.getCode(), division.getName());
    }

    public UserDto toUserDto(AppUser user) {
        if (user == null) {
            return null;
        }
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole() == null ? null : user.getRole().name(), user.getKeycloakUserId() != null);
    }
}
