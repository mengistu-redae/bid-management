package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.StageTarget;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.StageTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StageTargetService {

    private final StageTargetRepository stageTargetRepository;
    private final DivisionRepository divisionRepository;

    public List<StageTarget> list() {
        return stageTargetRepository.findAllByOrderByEntityTypeAscStageValueAsc();
    }

    public StageTarget get(UUID id) {
        return stageTargetRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Stage target not found: " + id));
    }

    @Transactional
    public StageTarget create(StageTarget target) {
        return stageTargetRepository.save(target);
    }

    @Transactional
    public StageTarget update(UUID id, StageTarget updates) {
        StageTarget target = get(id);
        target.setTargetType(updates.getTargetType());
        target.setTargetDays(updates.getTargetDays());
        target.setUsesBusinessDays(updates.isUsesBusinessDays());
        target.setDeadlineField(updates.getDeadlineField());
        target.setDeadlineOffsetDays(updates.getDeadlineOffsetDays());
        target.setDivision(updates.getDivision());
        target.setSector(updates.getSector());
        target.setOemName(updates.getOemName());
        target.setShipmentMode(updates.getShipmentMode());
        target.setStaleAfterDays(updates.getStaleAfterDays());
        target.setActive(updates.isActive());
        target.setNotes(updates.getNotes());
        return stageTargetRepository.save(target);
    }

    public com.motiengineering.bidmgmt.domain.Division division(UUID id) {
        return id == null ? null : divisionRepository.findById(id).orElse(null);
    }
}
