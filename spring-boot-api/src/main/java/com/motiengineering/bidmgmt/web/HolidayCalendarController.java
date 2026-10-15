package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.HolidayCalendarEntry;
import com.motiengineering.bidmgmt.dto.HolidayCalendarEntryDto;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.HolidayCalendarService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Director-only writes - reads are open since the calendar affects every stage-clock calculation everyone sees. */
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayCalendarController {

    private final HolidayCalendarService holidayCalendarService;

    public record UpsertHolidayRequest(@NotNull LocalDate holidayDate, @NotBlank String name) {
    }

    @GetMapping
    public List<HolidayCalendarEntryDto> list() {
        return holidayCalendarService.list().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<HolidayCalendarEntryDto> create(@Valid @RequestBody UpsertHolidayRequest request) {
        requireDirector();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(holidayCalendarService.create(request.holidayDate(), request.name())));
    }

    @PutMapping("/{id}")
    @Transactional
    public HolidayCalendarEntryDto update(@PathVariable UUID id, @Valid @RequestBody UpsertHolidayRequest request) {
        requireDirector();
        return toDto(holidayCalendarService.update(id, request.holidayDate(), request.name()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        requireDirector();
        holidayCalendarService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private HolidayCalendarEntryDto toDto(HolidayCalendarEntry entry) {
        return new HolidayCalendarEntryDto(entry.getId(), entry.getHolidayDate(), entry.getName());
    }

    private void requireDirector() {
        var user = CurrentUserContext.getUser();
        if (user == null || user.getRole() != com.motiengineering.bidmgmt.domain.enums.Role.DIRECTOR) {
            throw new AccessDeniedException("Only the Director can edit the holiday calendar");
        }
    }
}
