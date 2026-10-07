package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.ActivityLogEntry;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogEntry addNote(EntityType entityType, UUID entityId, AppUser author, String note) {
        return activityLogRepository.save(new ActivityLogEntry(entityType, entityId, author, note));
    }

    public List<ActivityLogEntry> history(EntityType entityType, UUID entityId) {
        return activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
