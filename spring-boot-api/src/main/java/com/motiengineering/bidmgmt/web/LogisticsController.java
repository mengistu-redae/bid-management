package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.LogisticsShipment;
import com.motiengineering.bidmgmt.domain.enums.LogisticsStatus;
import com.motiengineering.bidmgmt.dto.CreateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.dto.LogisticsShipmentDto;
import com.motiengineering.bidmgmt.dto.StatusChangeRequest;
import com.motiengineering.bidmgmt.dto.UpdateLogisticsShipmentRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.LogisticsMapper;
import com.motiengineering.bidmgmt.service.LogisticsService;
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

@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogisticsController {

    private final LogisticsService logisticsService;
    private final LogisticsMapper logisticsMapper;
    private final BidAccessService bidAccessService;

    @GetMapping
    public List<LogisticsShipmentDto> forContract(@RequestParam UUID contractId) {
        return logisticsService.forContract(contractId).stream().map(logisticsMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public LogisticsShipmentDto get(@PathVariable UUID id) {
        return logisticsMapper.toDto(logisticsService.get(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<LogisticsShipmentDto> create(@RequestBody CreateLogisticsShipmentRequest request) {
        LogisticsShipment shipment = logisticsService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(logisticsMapper.toDto(shipment));
    }

    @PutMapping("/{id}")
    @Transactional
    public LogisticsShipmentDto update(@PathVariable UUID id, @RequestBody UpdateLogisticsShipmentRequest request) {
        requireEdit(id);
        return logisticsMapper.toDto(logisticsService.update(id, request, CurrentUserContext.requireUser()));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public LogisticsShipmentDto changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        requireEdit(id);
        LogisticsStatus newStatus = LogisticsStatus.valueOf(request.newStatus().toUpperCase());
        return logisticsMapper.toDto(logisticsService.transitionStatus(id, newStatus, request.reason(), CurrentUserContext.requireUser()));
    }

    private void requireEdit(UUID id) {
        if (!bidAccessService.canEditLogistics(logisticsService.get(id))) {
            throw new AccessDeniedException("Not permitted to edit this shipment");
        }
    }
}
