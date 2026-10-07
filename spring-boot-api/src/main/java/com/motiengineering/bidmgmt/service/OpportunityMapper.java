package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.OpportunityDivision;
import com.motiengineering.bidmgmt.domain.OpportunityOem;
import com.motiengineering.bidmgmt.dto.DivisionDto;
import com.motiengineering.bidmgmt.dto.OemDto;
import com.motiengineering.bidmgmt.dto.OpportunityDto;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.OemRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityOemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OpportunityMapper {

    private final OpportunityDivisionRepository opportunityDivisionRepository;
    private final OpportunityOemRepository opportunityOemRepository;
    private final DivisionRepository divisionRepository;
    private final OemRepository oemRepository;
    private final BidMapper bidMapper;
    private final BidAccessService bidAccessService;

    public OpportunityDto toDto(Opportunity opportunity) {
        List<DivisionDto> divisions = opportunityDivisionRepository.findById_OpportunityId(opportunity.getId()).stream()
                .map((OpportunityDivision od) -> divisionRepository.findById(od.getId().getDivisionId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(bidMapper::toDto)
                .toList();
        List<OemDto> oems = opportunityOemRepository.findById_OpportunityId(opportunity.getId()).stream()
                .map((OpportunityOem oo) -> oemRepository.findById(oo.getId().getOemId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toDto)
                .toList();

        return new OpportunityDto(
                opportunity.getId(),
                bidMapper.toDto(opportunity.getOrganization()),
                opportunity.getTitle(),
                opportunity.getEstimatedValue(),
                opportunity.getEstimatedValueCurrency() == null ? null : opportunity.getEstimatedValueCurrency().name(),
                opportunity.getExpectedTenderDate(),
                opportunity.getStage() == null ? null : opportunity.getStage().name(),
                bidMapper.toUserDto(opportunity.getOwner()),
                opportunity.getLostReason(),
                opportunity.getConvertedBid() == null ? null : opportunity.getConvertedBid().getId(),
                opportunity.getNotes(),
                divisions,
                oems,
                bidAccessService.canEditOpportunity(opportunity),
                opportunity.getCreatedAt(),
                opportunity.getUpdatedAt());
    }

    public OemDto toDto(Oem oem) {
        return new OemDto(oem.getId(), oem.getName());
    }
}
