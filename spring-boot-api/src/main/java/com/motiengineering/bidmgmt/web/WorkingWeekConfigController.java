package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.WorkingWeekConfig;
import com.motiengineering.bidmgmt.dto.WorkingWeekConfigDto;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.WorkingWeekConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Director-only writes - reads are open since the working week affects every stage-clock calculation everyone sees. */
@RestController
@RequestMapping("/api/working-week-config")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkingWeekConfigController {

    private final WorkingWeekConfigService workingWeekConfigService;

    public record UpdateWorkingWeekConfigRequest(List<String> workingDays) {
    }

    @GetMapping
    public WorkingWeekConfigDto get() {
        return toDto(workingWeekConfigService.get());
    }

    @PutMapping
    @Transactional
    public WorkingWeekConfigDto update(@RequestBody UpdateWorkingWeekConfigRequest request) {
        var user = CurrentUserContext.getUser();
        if (user == null || user.getRole() != com.motiengineering.bidmgmt.domain.enums.Role.DIRECTOR) {
            throw new AccessDeniedException("Only the Director can edit the working week");
        }
        return toDto(workingWeekConfigService.update(request.workingDays(), user));
    }

    private WorkingWeekConfigDto toDto(WorkingWeekConfig config) {
        List<String> workingDays = config.workingDaySet().stream()
                .sorted()
                .map(Enum::name)
                .toList();
        return new WorkingWeekConfigDto(workingDays);
    }
}
