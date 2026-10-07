package com.motiengineering.bidmgmt.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConfirmTrackerRequest(UUID token, Map<String, List<ImportRowEditDto>> editsBySheet) {
}
