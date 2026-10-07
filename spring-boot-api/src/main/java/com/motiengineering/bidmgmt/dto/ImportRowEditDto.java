package com.motiengineering.bidmgmt.dto;

/**
 * A user correction to one previewed row before confirm, keyed by its
 * position in the preview list (stable within one token's lifetime - see
 * ImportPreviewCache). A blank field means "leave the parsed value as is";
 * skip means "don't import this row at all".
 */
public record ImportRowEditDto(int rowIndex, String organizationRaw, String cleanTitle, String divisionCode, boolean skip) {
}
