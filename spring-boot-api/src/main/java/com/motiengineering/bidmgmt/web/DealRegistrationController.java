package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ActivityEntryDto;
import com.motiengineering.bidmgmt.dto.ActivityNoteRequest;
import com.motiengineering.bidmgmt.dto.CreateDealRegistrationRequest;
import com.motiengineering.bidmgmt.dto.DealRegistrationDto;
import com.motiengineering.bidmgmt.dto.UpdateDealRegistrationRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ActivityLogService;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.DealRegistrationMapper;
import com.motiengineering.bidmgmt.service.DealRegistrationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** @Transactional(readOnly) by default - see BidController's class javadoc for why. */
@RestController
@RequestMapping("/api/deal-registrations")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealRegistrationController {

    private final DealRegistrationService dealRegistrationService;
    private final DealRegistrationMapper dealRegistrationMapper;
    private final BidAccessService bidAccessService;
    private final ActivityLogService activityLogService;

    @GetMapping
    public List<DealRegistrationDto> list() {
        return dealRegistrationService.list().stream().map(dealRegistrationMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public DealRegistrationDto get(@PathVariable UUID id) {
        return dealRegistrationMapper.toDto(dealRegistrationService.get(id));
    }

    @GetMapping("/conflicts")
    public List<DealRegistrationDto> conflicts(@RequestParam UUID organizationId, @RequestParam UUID oemId, @RequestParam(required = false) UUID excludingId) {
        return dealRegistrationService.conflictsFor(organizationId, oemId, excludingId).stream().map(dealRegistrationMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DealRegistrationDto> create(@RequestBody CreateDealRegistrationRequest request) {
        DealRegistration reg = dealRegistrationService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(dealRegistrationMapper.toDto(reg));
    }

    @PutMapping("/{id}")
    @Transactional
    public DealRegistrationDto update(@PathVariable UUID id, @RequestBody UpdateDealRegistrationRequest request) {
        requireEdit(id);
        return dealRegistrationMapper.toDto(dealRegistrationService.update(id, request));
    }

    @GetMapping("/{id}/activity")
    public List<ActivityEntryDto> activity(@PathVariable UUID id) {
        return activityLogService.history(EntityType.DEAL_REGISTRATION, id).stream()
                .map(a -> new ActivityEntryDto(a.getId(), a.getAuthor() == null ? null : a.getAuthor().getFullName(), a.getNote(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/activity")
    @Transactional
    public ActivityEntryDto addActivity(@PathVariable UUID id, @Valid @RequestBody ActivityNoteRequest request) {
        var entry = activityLogService.addNote(EntityType.DEAL_REGISTRATION, id, CurrentUserContext.requireUser(), request.note());
        return new ActivityEntryDto(entry.getId(), entry.getAuthor() == null ? null : entry.getAuthor().getFullName(), entry.getNote(), entry.getCreatedAt());
    }

    private void requireEdit(UUID id) {
        if (!bidAccessService.canEditDealRegistration(dealRegistrationService.get(id))) {
            throw new AccessDeniedException("Not permitted to edit this deal registration");
        }
    }
}
