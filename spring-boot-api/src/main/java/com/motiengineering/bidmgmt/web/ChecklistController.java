package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.dto.AddChecklistItemRequest;
import com.motiengineering.bidmgmt.dto.ChecklistItemDto;
import com.motiengineering.bidmgmt.dto.ChecklistItemUpdateRequest;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.ChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/** @Transactional on every endpoint - see BidController's class javadoc. */
@RestController
@RequestMapping("/api/lots/{lotId}/checklist-items")
@RequiredArgsConstructor
@Transactional
public class ChecklistController {

    private final ChecklistService checklistService;
    private final BidLotRepository bidLotRepository;
    private final AppUserRepository appUserRepository;
    private final BidMapper bidMapper;
    private final BidAccessService bidAccessService;

    @PostMapping
    public ChecklistItemDto add(@PathVariable UUID lotId, @Valid @RequestBody AddChecklistItemRequest request) {
        BidLot lot = requireEditableLot(lotId);
        AppUser owner = request.ownerId() == null ? null : appUserRepository.findById(request.ownerId()).orElse(null);
        return bidMapper.toDto(checklistService.addCustomItem(lot, request.title(), owner, request.dueDate()));
    }

    @PutMapping("/{itemId}")
    public ChecklistItemDto update(@PathVariable UUID lotId, @PathVariable UUID itemId, @RequestBody ChecklistItemUpdateRequest request) {
        requireEditableLot(lotId);
        if (request.done() != null) {
            checklistService.setDone(itemId, request.done(), CurrentUserContext.requireUser());
        }
        AppUser owner = request.ownerId() == null ? null : appUserRepository.findById(request.ownerId()).orElse(null);
        return bidMapper.toDto(checklistService.update(itemId, owner, request.dueDate()));
    }

    private BidLot requireEditableLot(UUID lotId) {
        BidLot lot = bidLotRepository.findById(lotId).orElseThrow(() -> new NoSuchElementException("Lot not found: " + lotId));
        if (!bidAccessService.canEditLot(lot)) {
            throw new AccessDeniedException("Not permitted to edit this lot's checklist");
        }
        return lot;
    }
}
