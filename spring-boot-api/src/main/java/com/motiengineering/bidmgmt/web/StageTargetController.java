package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;
import com.motiengineering.bidmgmt.domain.enums.TargetType;
import com.motiengineering.bidmgmt.dto.StageTargetDto;
import com.motiengineering.bidmgmt.dto.UpsertStageTargetRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.StageTargetService;
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

/** Director-only - these numbers decide what "late" means everywhere else. */
@RestController
@RequestMapping("/api/stage-targets")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageTargetController {

    private final StageTargetService stageTargetService;

    @GetMapping
    public List<StageTargetDto> list() {
        return stageTargetService.list().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<StageTargetDto> create(@Valid @RequestBody UpsertStageTargetRequest request) {
        requireDirector();
        StageTarget target = fromRequest(new StageTarget(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(stageTargetService.create(target)));
    }

    @PutMapping("/{id}")
    @Transactional
    public StageTargetDto update(@PathVariable UUID id, @Valid @RequestBody UpsertStageTargetRequest request) {
        requireDirector();
        StageTarget updates = fromRequest(new StageTarget(), request);
        return toDto(stageTargetService.update(id, updates));
    }

    private StageTarget fromRequest(StageTarget target, UpsertStageTargetRequest request) {
        target.setEntityType(EntityType.valueOf(request.entityType().toUpperCase()));
        target.setStageValue(request.stageValue());
        target.setTargetType(TargetType.valueOf(request.targetType().toUpperCase()));
        target.setTargetDays(request.targetDays());
        target.setUsesBusinessDays(request.usesBusinessDays());
        target.setDeadlineField(request.deadlineField());
        target.setDeadlineOffsetDays(request.deadlineOffsetDays());
        target.setDivision(stageTargetService.division(request.divisionId()));
        target.setSector(request.sector() == null || request.sector().isBlank() ? null : Sector.valueOf(request.sector().toUpperCase()));
        target.setOemName(request.oemName());
        target.setShipmentMode(request.shipmentMode() == null || request.shipmentMode().isBlank() ? null : ShipmentMode.valueOf(request.shipmentMode().toUpperCase()));
        target.setStaleAfterDays(request.staleAfterDays());
        target.setActive(request.active());
        target.setNotes(request.notes());
        return target;
    }

    private StageTargetDto toDto(StageTarget target) {
        Division division = target.getDivision();
        return new StageTargetDto(
                target.getId(),
                target.getEntityType() == null ? null : target.getEntityType().name(),
                target.getStageValue(),
                target.getTargetType() == null ? null : target.getTargetType().name(),
                target.getTargetDays(),
                target.isUsesBusinessDays(),
                target.getDeadlineField(),
                target.getDeadlineOffsetDays(),
                division == null ? null : division.getId(),
                division == null ? null : division.getName(),
                target.getSector() == null ? null : target.getSector().name(),
                target.getOemName(),
                target.getShipmentMode() == null ? null : target.getShipmentMode().name(),
                target.getStaleAfterDays(),
                target.isActive(),
                target.getNotes());
    }

    private void requireDirector() {
        var user = CurrentUserContext.getUser();
        if (user == null || user.getRole() != com.motiengineering.bidmgmt.domain.enums.Role.DIRECTOR) {
            throw new AccessDeniedException("Only the Director can edit stage targets");
        }
    }
}
