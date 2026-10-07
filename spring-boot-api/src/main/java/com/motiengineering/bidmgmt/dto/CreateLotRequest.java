package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateLotRequest(
        String lotLabel,
        String description,
        String divisionCode,
        BigDecimal estimatedValue,
        String estimatedValueCurrency,
        BigDecimal bidBondAmount,
        String bidBondCurrency,
        Integer bidBondValidityDays,
        String bidBondForm,
        String oemBrand,
        List<String> scopeTypes,
        List<String> accountOfficerNames) {
}
