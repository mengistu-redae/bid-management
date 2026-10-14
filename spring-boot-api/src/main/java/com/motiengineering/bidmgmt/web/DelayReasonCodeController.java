package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DelayReasonCodeDto;
import com.motiengineering.bidmgmt.service.DelayReasonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/delay-reason-codes")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelayReasonCodeController {

    private final DelayReasonCodeService delayReasonCodeService;

    public record CreateRequest(String code, String label, int sortOrder) {
    }

    public record UpdateRequest(String label, boolean active, int sortOrder) {
    }

    @GetMapping
    public List<DelayReasonCodeDto> list() {
        return delayReasonCodeService.listAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DelayReasonCodeDto> create(@RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(delayReasonCodeService.create(request.code(), request.label(), request.sortOrder())));
    }

    @PutMapping("/{id}")
    @Transactional
    public DelayReasonCodeDto update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        return toDto(delayReasonCodeService.update(id, request.label(), request.active(), request.sortOrder()));
    }

    private DelayReasonCodeDto toDto(com.motiengineering.bidmgmt.domain.DelayReasonCode entry) {
        return new DelayReasonCodeDto(entry.getId(), entry.getCode(), entry.getLabel(), entry.isActive(), entry.getSortOrder());
    }
}
