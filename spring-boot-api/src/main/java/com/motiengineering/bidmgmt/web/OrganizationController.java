package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.OrganizationDto;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.OrganizationResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final BidMapper bidMapper;

    @GetMapping
    public List<OrganizationDto> list(@RequestParam(required = false) String q) {
        List<Organization> orgs = q == null || q.isBlank()
                ? organizationRepository.findAll()
                : organizationRepository.findByNameContainingIgnoreCase(q);
        return orgs.stream().map(bidMapper::toDto).toList();
    }

    @PostMapping
    public OrganizationDto create(@RequestParam String name, @RequestParam(required = false) String sector) {
        Organization org = organizationResolutionService.resolveOrCreate(name, sector == null ? Sector.OTHER : Sector.valueOf(sector.toUpperCase()));
        return bidMapper.toDto(org);
    }
}
