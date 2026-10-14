package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.DealRegistrationDto;
import com.motiengineering.bidmgmt.dto.IndicatorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DealRegistrationMapper {

    private static final int EXPIRING_SOON_DAYS = 30;

    private final DealRegistrationService dealRegistrationService;
    private final OpportunityMapper opportunityMapper;
    private final BidMapper bidMapper;
    private final IndicatorService indicatorService;

    public DealRegistrationDto toDto(DealRegistration reg) {
        int conflicts = dealRegistrationService.conflictsFor(reg.getOrganization().getId(), reg.getOem().getId(), reg.getId()).size();
        boolean expiringSoon = reg.getExpiryDate() != null
                && !reg.getExpiryDate().isBefore(LocalDate.now())
                && reg.getExpiryDate().isBefore(LocalDate.now().plusDays(EXPIRING_SOON_DAYS));
        IndicatorDto indicator = reg.getStatus() == null ? null : indicatorFor(reg);

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
                indicator,
                reg.getCreatedAt(),
                reg.getUpdatedAt());
    }

    private IndicatorDto indicatorFor(DealRegistration reg) {
        var status = reg.getStatus();
        if (status == com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus.APPROVED
                || status == com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus.REJECTED
                || status == com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus.EXPIRED) {
            return null;
        }
        TargetContext context = new TargetContext(null, reg.getOrganization() == null ? null : reg.getOrganization().getSector(), null, null);
        IndicatorService.IndicatorResult result = indicatorService.compute(
                EntityType.DEAL_REGISTRATION, reg.getId(), "status", status.name(), reg.getCreatedAt(), context, Map.of());
        return new IndicatorDto(result.level().name(), result.percentUsed(), result.daysUsed(), result.targetDays(), result.usesBusinessDays(), result.stale());
    }
}
