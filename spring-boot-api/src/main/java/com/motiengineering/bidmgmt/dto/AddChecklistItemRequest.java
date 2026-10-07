package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record AddChecklistItemRequest(@NotBlank String title, UUID ownerId, LocalDate dueDate) {
}
