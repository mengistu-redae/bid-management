package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.ChecklistItem;
import com.motiengineering.bidmgmt.domain.ChecklistItemTemplate;
import com.motiengineering.bidmgmt.repository.ChecklistItemRepository;
import com.motiengineering.bidmgmt.repository.ChecklistItemTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistItemTemplateRepository templateRepository;
    private final ChecklistItemRepository checklistItemRepository;

    /** Instantiates one ChecklistItem per active template against a freshly created lot. */
    @Transactional
    public void seedForLot(BidLot lot) {
        List<ChecklistItemTemplate> templates = templateRepository.findByActiveTrueOrderBySortOrder();
        for (ChecklistItemTemplate template : templates) {
            ChecklistItem item = new ChecklistItem();
            item.setLot(lot);
            item.setTemplate(template);
            item.setTitle(template.getTitle());
            item.setSortOrder(template.getSortOrder());
            checklistItemRepository.save(item);
            lot.getChecklistItems().add(item); // keep the in-memory graph consistent - see ImportService's note on this same pitfall
        }
    }

    @Transactional
    public ChecklistItem addCustomItem(BidLot lot, String title, AppUser owner, LocalDate dueDate) {
        ChecklistItem item = new ChecklistItem();
        item.setLot(lot);
        item.setTitle(title);
        item.setOwner(owner);
        item.setDueDate(dueDate);
        item.setSortOrder(nextSortOrder(lot.getId()));
        ChecklistItem saved = checklistItemRepository.save(item);
        lot.getChecklistItems().add(saved);
        return saved;
    }

    @Transactional
    public ChecklistItem setDone(UUID itemId, boolean done, AppUser actingUser) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Checklist item not found: " + itemId));
        item.setDone(done);
        item.setDoneAt(done ? Instant.now() : null);
        item.setDoneBy(done ? actingUser : null);
        return checklistItemRepository.save(item);
    }

    @Transactional
    public ChecklistItem update(UUID itemId, AppUser owner, LocalDate dueDate) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Checklist item not found: " + itemId));
        item.setOwner(owner);
        item.setDueDate(dueDate);
        return checklistItemRepository.save(item);
    }

    public List<ChecklistItem> forLot(UUID lotId) {
        return checklistItemRepository.findByLot_IdOrderBySortOrder(lotId);
    }

    public int progressPercent(UUID lotId) {
        List<ChecklistItem> items = forLot(lotId);
        if (items.isEmpty()) {
            return 0;
        }
        long done = items.stream().filter(ChecklistItem::isDone).count();
        return (int) Math.round(done * 100.0 / items.size());
    }

    private int nextSortOrder(UUID lotId) {
        return forLot(lotId).stream().mapToInt(ChecklistItem::getSortOrder).max().orElse(0) + 10;
    }
}
