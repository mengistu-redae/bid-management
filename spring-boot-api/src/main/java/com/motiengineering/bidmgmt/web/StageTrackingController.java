package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.HoldRequest;
import com.motiengineering.bidmgmt.dto.StageClockDto;
import com.motiengineering.bidmgmt.dto.StageHistoryEntryDto;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.HoldService;
import com.motiengineering.bidmgmt.service.StageClockService;
import com.motiengineering.bidmgmt.service.StageHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic, entity-agnostic endpoints for stage history, the stage clock,
 * and holds - one surface shared by every tracked entity (Opportunity,
 * DealRegistration, Bid, Lot, ChecklistItem, Contract, Logistics) instead
 * of duplicating these three endpoints on every entity's own controller.
 */
@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageTrackingController {

    private final StageHistoryService stageHistoryService;
    private final StageClockService stageClockService;
    private final HoldService holdService;

    @GetMapping("/api/stage-history")
    public List<StageHistoryEntryDto> history(@RequestParam EntityType entityType, @RequestParam UUID entityId) {
        return stageHistoryService.history(entityType, entityId).stream()
                .map(e -> new StageHistoryEntryDto(
                        e.getId(), e.getStageField(), e.getFromValue(), e.getToValue(), e.getNote(),
                        e.getChangedBy() == null ? null : e.getChangedBy().getFullName(),
                        e.getChangedAt(), e.isBackfilled()))
                .toList();
    }

    @GetMapping("/api/stage-clock")
    public StageClockDto clock(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam String field,
            @RequestParam(required = false) String currentValue,
            @RequestParam(required = false) Instant fallbackEnteredAt) {
        return stageClockService.clockFor(entityType, entityId, field, currentValue, fallbackEnteredAt);
    }

    @PostMapping("/api/holds")
    @Transactional
    public void startHold(@RequestParam EntityType entityType, @RequestParam UUID entityId, @Valid @RequestBody HoldRequest request) {
        holdService.start(entityType, entityId, request.reason(), CurrentUserContext.requireUser());
    }

    @DeleteMapping("/api/holds")
    @Transactional
    public void endHold(@RequestParam EntityType entityType, @RequestParam UUID entityId) {
        holdService.end(entityType, entityId, CurrentUserContext.requireUser());
    }

    @GetMapping("/api/holds/active")
    public boolean isOnHold(@RequestParam EntityType entityType, @RequestParam UUID entityId) {
        return holdService.isOnHold(entityType, entityId);
    }
}
