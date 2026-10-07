package com.motiengineering.bidmgmt.dto;

import java.util.List;
import java.util.UUID;

public record ConfirmUpcomingRequest(UUID token, List<ImportRowEditDto> edits) {
}
