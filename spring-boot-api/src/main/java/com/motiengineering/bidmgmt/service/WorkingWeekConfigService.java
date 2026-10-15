package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.WorkingWeekConfig;
import com.motiengineering.bidmgmt.repository.WorkingWeekConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

/** Single row (id=1) - BusinessDayCalendarService reads this on every business-day calculation; see WorkingWeekConfig's own javadoc for the storage format. */
@Service
@RequiredArgsConstructor
public class WorkingWeekConfigService {

    private final WorkingWeekConfigRepository workingWeekConfigRepository;

    public WorkingWeekConfig get() {
        return workingWeekConfigRepository.findById(1).orElseGet(WorkingWeekConfig::new);
    }

    @Transactional
    public WorkingWeekConfig update(List<String> workingDayNames, AppUser actingUser) {
        if (workingDayNames == null || workingDayNames.isEmpty()) {
            throw new IllegalArgumentException("At least one working day is required");
        }
        WorkingWeekConfig config = workingWeekConfigRepository.findById(1).orElseGet(WorkingWeekConfig::new);
        config.setId(1);
        config.setWorkingDays(workingDayNames.stream()
                .map(name -> DayOfWeek.valueOf(name.toUpperCase()).name().substring(0, 3))
                .collect(Collectors.joining(",")));
        config.setUpdatedBy(actingUser);
        return workingWeekConfigRepository.save(config);
    }
}
