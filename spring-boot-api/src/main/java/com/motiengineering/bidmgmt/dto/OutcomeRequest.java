package com.motiengineering.bidmgmt.dto;

import java.math.BigDecimal;

public record OutcomeRequest(String outcome, String winnerName, BigDecimal winningPrice, String winningPriceCurrency) {
}
