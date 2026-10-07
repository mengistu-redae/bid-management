package com.motiengineering.bidmgmt.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/** divisionIds only matters when role is DIVISION_MANAGER - see UserService. */
public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank String email,
        @NotBlank String role,
        List<UUID> divisionIds) {
}
