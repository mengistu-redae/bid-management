package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(@NotBlank String reason) {
}
