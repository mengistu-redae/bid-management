package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Attachment;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.AttachmentDto;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** @Transactional(readOnly) by default - see BidController's class javadoc (toDto below reads the lazy uploadedBy association). */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public List<AttachmentDto> list(@RequestParam String entityType, @RequestParam UUID entityId) {
        return attachmentService.list(EntityType.valueOf(entityType.toUpperCase()), entityId).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    @Transactional
    public AttachmentDto upload(@RequestParam String entityType, @RequestParam UUID entityId, @RequestParam MultipartFile file) throws IOException {
        Attachment attachment = attachmentService.store(EntityType.valueOf(entityType.toUpperCase()), entityId, file, CurrentUserContext.requireUser());
        return toDto(attachment);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        Attachment attachment = attachmentService.get(id);
        InputStreamResource resource = new InputStreamResource(attachmentService.openStream(attachment));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFilename() + "\"")
                .contentType(attachment.getContentType() != null ? MediaType.parseMediaType(attachment.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private AttachmentDto toDto(Attachment a) {
        return new AttachmentDto(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(),
                a.getUploadedBy() == null ? null : a.getUploadedBy().getFullName(), a.getUploadedAt());
    }
}
