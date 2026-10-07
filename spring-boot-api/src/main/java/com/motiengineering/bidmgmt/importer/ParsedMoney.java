package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.enums.Currency;

import java.math.BigDecimal;

/**
 * Result of trying to parse a free-text money value (deal size, bid bond
 * amount). {@code needsReview} is set whenever the source text couldn't be
 * parsed with confidence (e.g. "USD 1 10.000.00") - the row is still
 * imported, just flagged so a human checks the actual tender document.
 */
public record ParsedMoney(BigDecimal amount, Currency currency, boolean needsReview, String reviewNote) {

    public static ParsedMoney of(BigDecimal amount, Currency currency) {
        return new ParsedMoney(amount, currency, false, null);
    }

    public static ParsedMoney flagged(String rawText, String note) {
        return new ParsedMoney(null, null, true, note + " (raw: \"" + rawText + "\")");
    }

    public static ParsedMoney empty() {
        return new ParsedMoney(null, null, false, null);
    }
}
