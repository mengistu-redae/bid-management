package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Key-date change history reuses the existing generic audit_log table
 * (AuditService.recordIfChanged) rather than a new table - callers already
 * audit the fields that matter (Bid.closingAt, etc.); this just reports on
 * a curated set of "key date" field names per entity, computing original
 * vs current value, how many times it changed, and total slippage in
 * days (current - original, so a date pushed back is positive slippage).
 */
@Service
@RequiredArgsConstructor
public class KeyDateHistoryService {

    private final AuditLogRepository auditLogRepository;

    public record KeyDateSummary(String fieldName, String originalValue, String currentValue, int changeCount, Long slippageDays) {
    }

    public List<KeyDateSummary> summarize(String entityType, UUID entityId, List<String> keyDateFields) {
        return keyDateFields.stream()
                .map(field -> summarizeField(entityType, entityId, field))
                .filter(s -> s.changeCount() > 0)
                .toList();
    }

    private KeyDateSummary summarizeField(String entityType, UUID entityId, String fieldName) {
        List<AuditLogEntry> entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId).stream()
                .filter(e -> e.getFieldName().equals(fieldName))
                .sorted(Comparator.comparing(AuditLogEntry::getChangedAt))
                .toList();
        if (entries.isEmpty()) {
            return new KeyDateSummary(fieldName, null, null, 0, null);
        }
        String original = entries.get(0).getOldValue();
        String current = entries.get(entries.size() - 1).getNewValue();
        Long slippage = slippageDays(original, current);
        return new KeyDateSummary(fieldName, original, current, entries.size(), slippage);
    }

    private Long slippageDays(String original, String current) {
        if (original == null || current == null) {
            return null;
        }
        try {
            return Duration.between(Instant.parse(original), Instant.parse(current)).toDays();
        } catch (DateTimeParseException e) {
            // Fall through to LocalDate (e.g. DealRegistration's expiryDate, which audits as "2026-01-07").
        }
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(original), LocalDate.parse(current));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
