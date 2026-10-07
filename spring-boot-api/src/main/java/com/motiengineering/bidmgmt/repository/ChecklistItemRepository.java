package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {
    List<ChecklistItem> findByLot_IdOrderBySortOrder(UUID lotId);
}
