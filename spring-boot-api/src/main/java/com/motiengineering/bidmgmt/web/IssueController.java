package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.dto.ActivityEntryDto;
import com.motiengineering.bidmgmt.dto.ActivityNoteRequest;
import com.motiengineering.bidmgmt.dto.CreateIssueRequest;
import com.motiengineering.bidmgmt.dto.IssueDto;
import com.motiengineering.bidmgmt.dto.UpdateIssueRequest;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.ActivityLogService;
import com.motiengineering.bidmgmt.service.BidAccessService;
import com.motiengineering.bidmgmt.service.IssueMapper;
import com.motiengineering.bidmgmt.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** @Transactional(readOnly) by default - see BidController's class javadoc for why. */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueController {

    private final IssueService issueService;
    private final IssueMapper issueMapper;
    private final BidAccessService bidAccessService;
    private final ActivityLogService activityLogService;

    @GetMapping
    public List<IssueDto> list() {
        return issueService.list().stream().map(issueMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public IssueDto get(@PathVariable UUID id) {
        return issueMapper.toDto(issueService.get(id));
    }

    /** Non-blocking - surfaces other open issues for the same organization+item so the "new issue" form can warn before submit. */
    @GetMapping("/open-duplicates")
    public List<IssueDto> openDuplicates(@RequestParam UUID organizationId, @RequestParam String itemOrSystem) {
        return issueService.openDuplicatesFor(organizationId, itemOrSystem).stream().map(issueMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<IssueDto> create(@RequestBody CreateIssueRequest request) {
        Issue issue = issueService.create(request, CurrentUserContext.requireUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(issueMapper.toDto(issue));
    }

    @PutMapping("/{id}")
    @Transactional
    public IssueDto update(@PathVariable UUID id, @RequestBody UpdateIssueRequest request) {
        requireEdit(id);
        return issueMapper.toDto(issueService.update(id, request, CurrentUserContext.requireUser()));
    }

    @GetMapping("/{id}/activity")
    public List<ActivityEntryDto> activity(@PathVariable UUID id) {
        return activityLogService.history(EntityType.ISSUE, id).stream()
                .map(a -> new ActivityEntryDto(a.getId(), a.getAuthor() == null ? null : a.getAuthor().getFullName(), a.getNote(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/activity")
    @Transactional
    public ActivityEntryDto addActivity(@PathVariable UUID id, @Valid @RequestBody ActivityNoteRequest request) {
        var entry = activityLogService.addNote(EntityType.ISSUE, id, CurrentUserContext.requireUser(), request.note());
        return new ActivityEntryDto(entry.getId(), entry.getAuthor() == null ? null : entry.getAuthor().getFullName(), entry.getNote(), entry.getCreatedAt());
    }

    private void requireEdit(UUID id) {
        if (!bidAccessService.canEditIssue(issueService.get(id))) {
            throw new AccessDeniedException("Not permitted to edit this issue");
        }
    }
}
