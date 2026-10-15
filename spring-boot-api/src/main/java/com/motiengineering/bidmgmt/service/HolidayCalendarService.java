package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.HolidayCalendarEntry;
import com.motiengineering.bidmgmt.repository.HolidayCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Admin-editable holiday calendar BusinessDayCalendarService reads on every business-day calculation - see V9's migration comment on the seeded 2026 dates. */
@Service
@RequiredArgsConstructor
public class HolidayCalendarService {

    private final HolidayCalendarRepository holidayCalendarRepository;

    public List<HolidayCalendarEntry> list() {
        return holidayCalendarRepository.findAll().stream()
                .sorted(Comparator.comparing(HolidayCalendarEntry::getHolidayDate))
                .toList();
    }

    @Transactional
    public HolidayCalendarEntry create(LocalDate holidayDate, String name) {
        requireUnused(holidayDate, null);
        return holidayCalendarRepository.save(new HolidayCalendarEntry(holidayDate, name));
    }

    @Transactional
    public HolidayCalendarEntry update(UUID id, LocalDate holidayDate, String name) {
        HolidayCalendarEntry entry = holidayCalendarRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Holiday not found: " + id));
        requireUnused(holidayDate, id);
        entry.setHolidayDate(holidayDate);
        entry.setName(name);
        return holidayCalendarRepository.save(entry);
    }

    @Transactional
    public void delete(UUID id) {
        holidayCalendarRepository.deleteById(id);
    }

    private void requireUnused(LocalDate holidayDate, UUID excludingId) {
        boolean exists = holidayCalendarRepository.findAll().stream()
                .anyMatch(e -> e.getHolidayDate().equals(holidayDate) && !e.getId().equals(excludingId));
        if (exists) {
            throw new IllegalArgumentException("A holiday is already recorded on " + holidayDate);
        }
    }
}
