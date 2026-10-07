package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.repository.OemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OemService {

    private final OemRepository oemRepository;

    public List<Oem> list() {
        return oemRepository.findAll();
    }

    @Transactional
    public Oem resolveOrCreate(String name) {
        String trimmed = name.trim();
        return oemRepository.findByNameIgnoreCase(trimmed).orElseGet(() -> oemRepository.save(new Oem(trimmed)));
    }
}
