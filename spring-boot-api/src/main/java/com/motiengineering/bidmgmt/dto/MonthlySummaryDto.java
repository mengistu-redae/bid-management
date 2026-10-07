package com.motiengineering.bidmgmt.dto;

import java.util.List;

public record MonthlySummaryDto(int year, int month, List<MonthlySummaryRowDto> rows) {
}
