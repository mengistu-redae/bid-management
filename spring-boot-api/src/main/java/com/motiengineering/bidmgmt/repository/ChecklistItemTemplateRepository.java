package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.ChecklistItemTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChecklistItemTemplateRepository extends JpaRepository<ChecklistItemTemplate, UUID> {
    List<ChecklistItemTemplate> findByActiveTrueOrderBySortOrder();
}
