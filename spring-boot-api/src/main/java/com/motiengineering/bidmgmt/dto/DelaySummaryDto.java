package com.motiengineering.bidmgmt.dto;

import java.util.List;

public record DelaySummaryDto(int greenCount, int amberCount, int redCount, int criticalCount, int staleCount, List<DelayRowDto> topDelayed) {
}
