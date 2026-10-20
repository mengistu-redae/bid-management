package com.motiengineering.bidmgmt.dto;

import java.util.List;
import java.util.UUID;

public record ConfirmIssuesRequest(UUID token, List<ImportRowEditDto> edits) {
}
