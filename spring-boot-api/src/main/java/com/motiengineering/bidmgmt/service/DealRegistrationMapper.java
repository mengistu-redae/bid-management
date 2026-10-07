package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.dto.DealRegistrationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DealRegistrationMapper {

    private static final int EXPIRING_SOON_DAYS = 30;

    private final DealRegistrationService dealRegistrationService;
    private final OpportunityMapper opportunityMapper;
    private final BidMapper bidMapper;

    public DealRegistrationDto toDto(DealRegistration reg) {
        int conflicts = dealRegistrationService.conflictsFor(reg.getOrganization().getId(), reg.getOem().getId(), reg.getId()).size();
        boolean expiringSoon = reg.getExpiryDate() != null
                && !reg.getExpiryDate().isBefore(LocalDate.now())
                && reg.getExpiryDate().isBefore(LocalDate.now().plusDays(EXPIRING_SOON_DAYS));

        return new DealRegistrationDto(
                reg.getId(),
                opportunityMapper.toDto(reg.getOem()),
                reg.getDistributor(),
                reg.getRegistrationId(),
                bidMapper.toDto(reg.getOrganization()),
                reg.getOpportunity() == null ? null : reg.getOpportunity().getId(),
                reg.getBid() == null ? null : reg.getBid().getId(),
                reg.getSubmittedDate(),
                reg.getStatus() == null ? null : reg.getStatus().name(),
                reg.getApprovalDate(),
                reg.getExpiryDate(),
                reg.getProtectedDiscountPercent(),
                reg.getSpecialPriceReference(),
                reg.getNotes(),
                conflicts,
                expiringSoon,
                reg.getCreatedAt(),
                reg.getUpdatedAt());
    }
}
