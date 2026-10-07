package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ActivityEntryDto;
import com.motiengineering.bidmgmt.dto.ActivityNoteRequest;
import com.motiengineering.bidmgmt.dto.BidDto;
import com.motiengineering.bidmgmt.dto.CreateBidRequest;
import com.motiengineering.bidmgmt.dto.CreateLotRequest;
import com.motiengineering.bidmgmt.dto.LotDto;
import com.motiengineering.bidmgmt.dto.StatusChangeRequest;
import com.motiengineering.bidmgmt.dto.StatusHistoryDto;
import com.motiengineering.bidmgmt.dto.UpdateBidRequest;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ActivityLogService;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.BidService;
import com.motiengineering.bidmgmt.service.BidStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Every method here is transactional (readOnly by default, read-write where
 * noted) because the DTO mapping below reads lazily-fetched associations
 * (lots, scouts, checklist items, ...) - with `open-in-view: false`, that
 * mapping has to happen inside the same Hibernate session as the fetch, not
 * after the service call has already returned and closed it. Found live:
 * GET /api/bids 500'd with LazyInitializationException on Bid.lots before
 * this was added.
 */
@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidController {

    private final BidService bidService;
    private final BidMapper bidMapper;
    private final BidAccessService bidAccessService;
    private final BidStatusService bidStatusService;
    private final ActivityLogService activityLogService;
    private final BidStatusHistoryRepository bidStatusHistoryRepository;
    private final com.motiengineering.bidmgmt.service.BidExportService bidExportService;

    @GetMapping
    public List<BidDto> list(
            @RequestParam(required = false) UUID divisionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) java.time.Instant closingFrom,
            @RequestParam(required = false) java.time.Instant closingTo) {
        var filter = new com.motiengineering.bidmgmt.dto.BidFilter(divisionId, status, officerId, organizationId, closingFrom, closingTo);
        return bidService.list(filter).stream().map(bidMapper::toDto).toList();
    }

    @GetMapping("/export")
    public ResponseEntity<org.springframework.core.io.ByteArrayResource> export(
            @RequestParam(required = false) UUID divisionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) java.time.Instant closingFrom,
            @RequestParam(required = false) java.time.Instant closingTo) {
        var filter = new com.motiengineering.bidmgmt.dto.BidFilter(divisionId, status, officerId, organizationId, closingFrom, closingTo);
        List<BidDto> rows = bidService.list(filter).stream().map(bidMapper::toDto).toList();
        byte[] xlsx = bidExportService.toExcel(rows);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"bids-export.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.ByteArrayResource(xlsx));
    }

    @GetMapping("/{id}")
    public BidDto get(@PathVariable UUID id) {
        Bid bid = bidService.get(id);
        requireView(bid);
        return bidMapper.toDto(bid);
    }

    @GetMapping("/duplicates")
    public List<BidDto> duplicates(@RequestParam UUID organizationId, @RequestParam String referenceNumber) {
        return bidService.findPossibleDuplicates(organizationId, referenceNumber).stream().map(bidMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<BidDto> create(@Valid @RequestBody CreateBidRequest request) {
        if (!bidAccessService.canCreate()) {
            throw new AccessDeniedException("Not permitted to create a bid");
        }
        Bid bid = bidService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(bidMapper.toDto(bid));
    }

    @PutMapping("/{id}")
    @Transactional
    public BidDto update(@PathVariable UUID id, @RequestBody UpdateBidRequest request) {
        Bid bid = bidService.get(id);
        requireEdit(bid);
        return bidMapper.toDto(bidService.update(id, request, CurrentUserContext.requireUser()));
    }

    @PostMapping("/{id}/lots")
    @Transactional
    public LotDto addLot(@PathVariable UUID id, @RequestBody CreateLotRequest request) {
        Bid bid = bidService.get(id);
        requireEdit(bid);
        BidLot lot = bidService.addLot(bid, request);
        return bidMapper.toDto(lot);
    }

    @PostMapping("/{id}/status")
    @Transactional
    public BidDto changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest request) {
        Bid bid = bidService.get(id);
        requireEdit(bid);
        BidStatus newStatus = parseStatus(request.newStatus());
        Bid updated = bidStatusService.transitionBid(bid, newStatus, request.reason(), CurrentUserContext.requireUser());
        return bidMapper.toDto(updated);
    }

    @GetMapping("/{id}/status-history")
    public List<StatusHistoryDto> statusHistory(@PathVariable UUID id) {
        Bid bid = bidService.get(id);
        requireView(bid);
        return bidStatusHistoryRepository.findByBid_IdOrderByChangedAt(id).stream()
                .map(h -> new StatusHistoryDto(
                        h.getId(),
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(),
                        h.getReason(),
                        h.getChangedBy() == null ? null : h.getChangedBy().getFullName(),
                        h.getChangedAt()))
                .toList();
    }

    @GetMapping("/{id}/activity")
    public List<ActivityEntryDto> activity(@PathVariable UUID id) {
        Bid bid = bidService.get(id);
        requireView(bid);
        return activityLogService.history(EntityType.BID, id).stream()
                .map(a -> new ActivityEntryDto(a.getId(), a.getAuthor() == null ? null : a.getAuthor().getFullName(), a.getNote(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/activity")
    @Transactional
    public ActivityEntryDto addActivity(@PathVariable UUID id, @Valid @RequestBody ActivityNoteRequest request) {
        Bid bid = bidService.get(id);
        requireView(bid);
        var entry = activityLogService.addNote(EntityType.BID, id, CurrentUserContext.requireUser(), request.note());
        return new ActivityEntryDto(entry.getId(), entry.getAuthor() == null ? null : entry.getAuthor().getFullName(), entry.getNote(), entry.getCreatedAt());
    }

    private BidStatus parseStatus(String raw) {
        try {
            return BidStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NoSuchElementException("Unknown status: " + raw);
        }
    }

    private void requireView(Bid bid) {
        if (!bidAccessService.canView(bid)) {
            throw new AccessDeniedException("Not permitted to view this bid");
        }
    }

    private void requireEdit(Bid bid) {
        if (!bidAccessService.canEdit(bid)) {
            throw new AccessDeniedException("Not permitted to edit this bid");
        }
    }
}
