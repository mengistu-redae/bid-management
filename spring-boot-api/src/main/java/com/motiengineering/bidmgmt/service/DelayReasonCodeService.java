package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.DelayReasonCode;
import com.motiengineering.bidmgmt.repository.DelayReasonCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelayReasonCodeService {

    private final DelayReasonCodeRepository delayReasonCodeRepository;

    public List<DelayReasonCode> listActive() {
        return delayReasonCodeRepository.findByActiveTrueOrderBySortOrder();
    }

    public List<DelayReasonCode> listAll() {
        return delayReasonCodeRepository.findAll();
    }

    @Transactional
    public DelayReasonCode create(String code, String label, int sortOrder) {
        DelayReasonCode entry = new DelayReasonCode();
        entry.setCode(code);
        entry.setLabel(label);
        entry.setSortOrder(sortOrder);
        return delayReasonCodeRepository.save(entry);
    }

    @Transactional
    public DelayReasonCode update(UUID id, String label, boolean active, int sortOrder) {
        DelayReasonCode entry = delayReasonCodeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Delay reason code not found: " + id));
        entry.setLabel(label);
        entry.setActive(active);
        entry.setSortOrder(sortOrder);
        return delayReasonCodeRepository.save(entry);
    }
}
