package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

/** One row of a pipeline-value breakdown (by division, or by status) - ETB and USD are always kept as separate columns, never summed together. */
public record PipelineValueRowDto(String groupLabel, BigDecimal totalEtb, BigDecimal totalUsd, int lotCount) {
}
