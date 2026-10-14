package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

/** One bucket of the dashboard's "by lifecycle stage" summary - a coarser grouping of BidStatus than pipelineByStatus's raw per-enum breakdown. */
public record LifecycleStageRowDto(String stageLabel, int bidCount, BigDecimal totalEtb) {
}
