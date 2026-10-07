package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusChangeRequest(@NotBlank String newStatus, String reason) {
}
