package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Contract;
import com.motiengineering.bidmgmt.domain.enums.ContractStatus;
import com.motiengineering.bidmgmt.dto.ContractDto;
import com.motiengineering.bidmgmt.dto.CreateContractRequest;
import com.motiengineering.bidmgmt.dto.StatusChangeRequest;
import com.motiengineering.bidmgmt.dto.UpdateContractRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.ContractMapper;
import com.motiengineering.bidmgmt.service.ContractService;
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

/** @Transactional(readOnly) by default - see BidController's class javadoc for why. */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractController {

    private final ContractService contractService;
    private final ContractMapper contractMapper;
    private final BidAccessService bidAccessService;

    @GetMapping
    public List<ContractDto> list() {
        return contractService.list().stream().map(contractMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public ContractDto get(@PathVariable UUID id) {
        return contractMapper.toDto(contractService.get(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ContractDto> create(@RequestBody CreateContractRequest request) {
        Contract contract = contractService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(contractMapper.toDto(contract));
    }

    @PutMapping("/{id}")
    @Transactional
    public ContractDto update(@PathVariable UUID id, @RequestBody UpdateContractRequest request) {
        requireEdit(id);
        return contractMapper.toDto(contractService.update(id, request, CurrentUserContext.requireUser()));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ContractDto changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        requireEdit(id);
        ContractStatus newStatus = ContractStatus.valueOf(request.newStatus().toUpperCase());
        return contractMapper.toDto(contractService.transitionStatus(id, newStatus, request.reason(), request.delayReasonCode(), CurrentUserContext.requireUser()));
    }

    private void requireEdit(UUID id) {
        if (!bidAccessService.canEditContract(contractService.get(id))) {
            throw new AccessDeniedException("Not permitted to edit this contract");
        }
    }
}
