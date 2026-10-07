package com.motiengineering.bidmgmt.dto;

import java.util.UUID;

public record ImportPreviewResponse<T>(UUID token, String sourceFilename, T data) {
}
