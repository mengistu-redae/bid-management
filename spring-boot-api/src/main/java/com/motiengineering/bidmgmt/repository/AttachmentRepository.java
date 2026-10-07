package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Attachment;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEntityTypeAndEntityIdOrderByUploadedAtDesc(EntityType entityType, UUID entityId);
}
