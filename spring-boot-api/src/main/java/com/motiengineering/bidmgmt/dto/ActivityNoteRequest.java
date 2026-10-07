package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivityNoteRequest(@NotBlank String note) {
}
