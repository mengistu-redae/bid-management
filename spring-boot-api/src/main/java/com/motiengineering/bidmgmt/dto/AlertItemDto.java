package com.motiengineering.bidmgmt.dto;

/** Entity-agnostic "one line in an alert/digest email" - built by the caller (who knows the real entity type) so EmailTemplates never needs to know about Bid/Lot/Opportunity/etc. directly. */
public record AlertItemDto(String title, String subtitle, String linkPath, String extra) {
}
