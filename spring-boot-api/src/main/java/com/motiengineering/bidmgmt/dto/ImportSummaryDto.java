package com.motiengineering.bidmgmt.dto;

public record ImportSummaryDto(int rowsTotal, int rowsCreated, int rowsMerged, int rowsFlagged, int rowsSkipped) {
}
