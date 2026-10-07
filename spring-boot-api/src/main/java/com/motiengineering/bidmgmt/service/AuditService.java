package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /** No-ops when oldValue and newValue are equal, so callers can call this unconditionally on every field they track. */
    public void recordIfChanged(String entityType, UUID entityId, String fieldName, Object oldValue, Object newValue, AppUser changedBy) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        auditLogRepository.save(new AuditLogEntry(
                entityType, entityId, fieldName,
                oldValue == null ? null : oldValue.toString(),
                newValue == null ? null : newValue.toString(),
                changedBy));
    }

    public List<AuditLogEntry> history(String entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId);
    }
}
