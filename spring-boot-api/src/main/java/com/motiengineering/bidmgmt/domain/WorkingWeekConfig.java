package com.motiengineering.bidmgmt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Single row (id fixed at 1, see V8's CHECK) holding the admin-editable set of working weekdays. */
@Entity
@Table(name = "working_week_config")
@Getter
@Setter
@NoArgsConstructor
public class WorkingWeekConfig {

    @Id
    private int id = 1;

    /** Comma-separated DayOfWeek names, e.g. "MON,TUE,WED,THU,FRI,SAT". */
    private String workingDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;

    public Set<DayOfWeek> workingDaySet() {
        if (workingDays == null || workingDays.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(workingDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> DayOfWeek.valueOf(shortToFullName(s)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static String shortToFullName(String abbreviation) {
        return switch (abbreviation.toUpperCase()) {
            case "MON" -> "MONDAY";
            case "TUE" -> "TUESDAY";
            case "WED" -> "WEDNESDAY";
            case "THU" -> "THURSDAY";
            case "FRI" -> "FRIDAY";
            case "SAT" -> "SATURDAY";
            case "SUN" -> "SUNDAY";
            default -> abbreviation.toUpperCase();
        };
    }
}
