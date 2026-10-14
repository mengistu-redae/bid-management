package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.FollowUpDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.FollowUpService;
import com.motiengineering.bidmgmt.service.MyFollowUpsService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowUpController {

    private final FollowUpService followUpService;
    private final MyFollowUpsService myFollowUpsService;
    private final AppUserRepository appUserRepository;

    public record LogFollowUpRequest(String note, LocalDate nextFollowUpDate, UUID ownerId) {
    }

    public record SnoozeRequest(@NotBlank String reason, LocalDate snoozedUntil) {
    }

    @GetMapping("/api/follow-up")
    public FollowUpDto get(@RequestParam EntityType entityType, @RequestParam UUID entityId) {
        return followUpService.get(entityType, entityId).map(this::toDto).orElse(null);
    }

    @PostMapping("/api/follow-up")
    @Transactional
    public FollowUpDto logFollowUp(@RequestParam EntityType entityType, @RequestParam UUID entityId, @RequestBody LogFollowUpRequest request) {
        AppUser owner = request.ownerId() == null ? null : appUserRepository.findById(request.ownerId()).orElse(null);
        FollowUpState state = followUpService.logFollowUp(entityType, entityId, request.note(), request.nextFollowUpDate(), owner, CurrentUserContext.requireUser());
        return toDto(state);
    }

    @PostMapping("/api/follow-up/snooze")
    @Transactional
    public FollowUpDto snooze(@RequestParam EntityType entityType, @RequestParam UUID entityId, @RequestBody SnoozeRequest request) {
        FollowUpState state = followUpService.snooze(entityType, entityId, request.snoozedUntil(), request.reason(), CurrentUserContext.requireUser());
        return toDto(state);
    }

    @GetMapping("/api/my-follow-ups")
    public MyFollowUpsService.MyFollowUpsDto myFollowUps() {
        return myFollowUpsService.build(CurrentUserContext.requireUser());
    }

    private FollowUpDto toDto(FollowUpState state) {
        if (state == null) {
            return null;
        }
        return new FollowUpDto(
                state.getNextFollowUpDate(),
                state.getFollowUpOwner() == null ? null : state.getFollowUpOwner().getId(),
                state.getFollowUpOwner() == null ? null : state.getFollowUpOwner().getFullName(),
                state.getSnoozedUntil(),
                state.getSnoozeReason(),
                state.getUpdatedAt());
    }
}
