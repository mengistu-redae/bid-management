package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ActivityEntryDto;
import com.motiengineering.bidmgmt.dto.ActivityNoteRequest;
import com.motiengineering.bidmgmt.dto.BidDto;
import com.motiengineering.bidmgmt.dto.CreateOpportunityRequest;
import com.motiengineering.bidmgmt.dto.OpportunityDto;
import com.motiengineering.bidmgmt.dto.UpdateOpportunityRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ActivityLogService;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.OpportunityMapper;
import com.motiengineering.bidmgmt.service.OpportunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** @Transactional(readOnly) by default - see BidController's class javadoc for why (DTO mapping reads lazy associations). */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final OpportunityMapper opportunityMapper;
    private final BidMapper bidMapper;
    private final BidAccessService bidAccessService;
    private final ActivityLogService activityLogService;

    @GetMapping
    public List<OpportunityDto> list() {
        return opportunityService.list().stream().map(opportunityMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public OpportunityDto get(@PathVariable UUID id) {
        return opportunityMapper.toDto(opportunityService.get(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OpportunityDto> create(@Valid @RequestBody CreateOpportunityRequest request) {
        Opportunity opportunity = opportunityService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(opportunityMapper.toDto(opportunity));
    }

    @PutMapping("/{id}")
    @Transactional
    public OpportunityDto update(@PathVariable UUID id, @RequestBody UpdateOpportunityRequest request) {
        requireEdit(id);
        return opportunityMapper.toDto(opportunityService.update(id, request));
    }

    @PostMapping("/{id}/convert-to-bid")
    @Transactional
    public BidDto convertToBid(@PathVariable UUID id) {
        requireEdit(id);
        return bidMapper.toDto(opportunityService.convertToBid(id, CurrentUserContext.requireUser()));
    }

    @GetMapping("/{id}/activity")
    public List<ActivityEntryDto> activity(@PathVariable UUID id) {
        return activityLogService.history(EntityType.OPPORTUNITY, id).stream()
                .map(a -> new ActivityEntryDto(a.getId(), a.getAuthor() == null ? null : a.getAuthor().getFullName(), a.getNote(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/activity")
    @Transactional
    public ActivityEntryDto addActivity(@PathVariable UUID id, @Valid @RequestBody ActivityNoteRequest request) {
        var entry = activityLogService.addNote(EntityType.OPPORTUNITY, id, CurrentUserContext.requireUser(), request.note());
        return new ActivityEntryDto(entry.getId(), entry.getAuthor() == null ? null : entry.getAuthor().getFullName(), entry.getNote(), entry.getCreatedAt());
    }

    private void requireEdit(UUID id) {
        if (!bidAccessService.canEditOpportunity(opportunityService.get(id))) {
            throw new AccessDeniedException("Not permitted to edit this opportunity");
        }
    }
}
