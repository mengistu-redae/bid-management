package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Admin-editable; seeded with best-effort 2026 Ethiopian public holidays - see V9's migration comment. */
@Entity
@Table(name = "holiday_calendar")
@Getter
@Setter
@NoArgsConstructor
public class HolidayCalendarEntry {

    @Id
    @UuidGenerator
    private UUID id;

    private LocalDate holidayDate;

    private String name;

    @CreationTimestamp
    private Instant createdAt;

    public HolidayCalendarEntry(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }
}
