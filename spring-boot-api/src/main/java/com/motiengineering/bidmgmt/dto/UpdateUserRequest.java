package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank String fullName,
        String email,
        @NotBlank String role,
        List<UUID> divisionIds,
        String telegramChatId) {
}
