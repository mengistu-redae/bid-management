package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.OemDto;
import com.motiengineering.bidmgmt.service.OemService;
import com.motiengineering.bidmgmt.service.OpportunityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/oems")
@RequiredArgsConstructor
public class OemController {

    private final OemService oemService;
    private final OpportunityMapper opportunityMapper;

    @GetMapping
    public List<OemDto> list() {
        return oemService.list().stream().map(opportunityMapper::toDto).toList();
    }
}
