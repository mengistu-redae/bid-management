package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DivisionDto;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.service.BidMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/divisions")
@RequiredArgsConstructor
public class DivisionController {

    private final DivisionRepository divisionRepository;
    private final BidMapper bidMapper;

    @GetMapping
    public List<DivisionDto> list() {
        return divisionRepository.findAll().stream().map(bidMapper::toDto).toList();
    }
}
