package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Attachment;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;

    @Value("${bidmgmt.uploads-root}")
    private String uploadsRoot;

    public Attachment store(EntityType entityType, UUID entityId, MultipartFile file, AppUser uploadedBy) throws IOException {
        Path dir = Path.of(uploadsRoot, entityType.name().toLowerCase(), entityId.toString());
        Files.createDirectories(dir);
        String storedName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        Path target = dir.resolve(storedName);
        file.transferTo(target);

        Attachment attachment = new Attachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFilename(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setStoragePath(target.toString());
        attachment.setUploadedBy(uploadedBy);
        return attachmentRepository.save(attachment);
    }

    public List<Attachment> list(EntityType entityType, UUID entityId) {
        return attachmentRepository.findByEntityTypeAndEntityIdOrderByUploadedAtDesc(entityType, entityId);
    }

    public Attachment get(UUID id) {
        return attachmentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Attachment not found: " + id));
    }

    public InputStream openStream(Attachment attachment) throws IOException {
        return Files.newInputStream(Path.of(attachment.getStoragePath()));
    }

    private String sanitize(String filename) {
        if (filename == null) {
            return "file";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
