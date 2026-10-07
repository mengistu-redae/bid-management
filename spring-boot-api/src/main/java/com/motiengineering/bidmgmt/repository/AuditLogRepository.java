package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByChangedAtDesc(String entityType, UUID entityId);
}
