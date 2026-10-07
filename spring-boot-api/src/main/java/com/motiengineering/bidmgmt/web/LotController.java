package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.Outcome;
import com.motiengineering.bidmgmt.dto.LotDto;
import com.motiengineering.bidmgmt.dto.OutcomeRequest;
import com.motiengineering.bidmgmt.dto.StatusChangeRequest;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.BidStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/** @Transactional on every endpoint here - see BidController's class javadoc for why (lazy associations touched both by requireEdit's access checks and by the DTO mapping). */
@RestController
@RequestMapping("/api/lots")
@RequiredArgsConstructor
@Transactional
public class LotController {

    private final BidLotRepository bidLotRepository;
    private final BidMapper bidMapper;
    private final BidAccessService bidAccessService;
    private final BidStatusService bidStatusService;

    @PostMapping("/{id}/status")
    public LotDto changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        BidLot lot = get(id);
        requireEdit(lot);
        BidStatus newStatus = parseStatus(request.newStatus());
        BidLot updated = bidStatusService.transitionLot(lot, newStatus, request.reason(), CurrentUserContext.requireUser());
        return bidMapper.toDto(updated);
    }

    @PostMapping("/{id}/outcome")
    public LotDto setOutcome(@PathVariable UUID id, @RequestBody OutcomeRequest request) {
        BidLot lot = get(id);
        requireEdit(lot);
        lot.setOutcome(request.outcome() == null ? null : Outcome.valueOf(request.outcome().toUpperCase()));
        lot.setWinnerName(request.winnerName());
        lot.setWinningPrice(request.winningPrice());
        lot.setWinningPriceCurrency(request.winningPriceCurrency() == null ? null : Currency.valueOf(request.winningPriceCurrency().toUpperCase()));
        return bidMapper.toDto(bidLotRepository.save(lot));
    }

    private BidLot get(UUID id) {
        return bidLotRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Lot not found: " + id));
    }

    private BidStatus parseStatus(String raw) {
        try {
            return BidStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NoSuchElementException("Unknown status: " + raw);
        }
    }

    private void requireEdit(BidLot lot) {
        if (!bidAccessService.canEditLot(lot)) {
            throw new AccessDeniedException("Not permitted to edit this lot");
        }
    }
}
