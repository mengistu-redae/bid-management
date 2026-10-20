package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.dto.IssueDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class IssueMapper {

    private final BidMapper bidMapper;

    public IssueDto toDto(Issue issue) {
        Instant end = issue.getResolvedAt() != null ? issue.getResolvedAt() : Instant.now();
        long daysOpen = issue.getOpenedAt() == null ? 0 : Duration.between(issue.getOpenedAt(), end).toDays();

        return new IssueDto(
                issue.getId(),
                issue.getCategory() == null ? null : issue.getCategory().name(),
                issue.getStatus() == null ? null : issue.getStatus().name(),
                issue.getSeverity() == null ? null : issue.getSeverity().name(),
                bidMapper.toDto(issue.getOrganization()),
                issue.getDivision() == null ? null : bidMapper.toDto(issue.getDivision()),
                issue.getItemOrSystem(),
                issue.getDescription(),
                issue.getCurrentStatusNote(),
                issue.getNextAction(),
                issue.getChallenge(),
                issue.getBid() == null ? null : issue.getBid().getId(),
                issue.getContract() == null ? null : issue.getContract().getId(),
                issue.getOpportunity() == null ? null : issue.getOpportunity().getId(),
                issue.getFinancialImpact(),
                issue.getFinancialImpactCurrency() == null ? null : issue.getFinancialImpactCurrency().name(),
                issue.getOpenedAt(),
                daysOpen,
                issue.getResolvedAt(),
                issue.getResolutionNotes(),
                bidMapper.toUserDto(issue.getAssignedTo()),
                bidMapper.toUserDto(issue.getCreatedBy()),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}
