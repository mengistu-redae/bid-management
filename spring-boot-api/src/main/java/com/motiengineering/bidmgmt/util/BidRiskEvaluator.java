package com.motiengineering.bidmgmt.util;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.ChecklistItem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shared "closing soon and still missing its MAF or vendor quote" check - originally dashboard-only, now also surfaced on the plain bid list. */
public final class BidRiskEvaluator {

    private static final String MAF_CHECKLIST_TITLE = "MAF from OEM";
    private static final String VENDOR_QUOTE_CHECKLIST_TITLE = "Vendor/distributor quotation";
    public static final int RED_FLAG_WINDOW_DAYS = 5;

    private BidRiskEvaluator() {
    }

    /** Null unless the bid is open, closing within RED_FLAG_WINDOW_DAYS of now, and still missing its MAF and/or vendor quote checklist item. */
    public static String redFlagReason(Bid bid, Instant now) {
        if (bid.getClosingAt() == null || bid.getStatus().isTerminal()) {
            return null;
        }
        if (!bid.getClosingAt().isBefore(now.plus(RED_FLAG_WINDOW_DAYS, ChronoUnit.DAYS))) {
            return null;
        }
        boolean missingMaf = false;
        boolean missingQuote = false;
        for (BidLot lot : bid.getLots()) {
            for (ChecklistItem item : lot.getChecklistItems()) {
                if (item.getTitle().equals(MAF_CHECKLIST_TITLE) && !item.isDone()) {
                    missingMaf = true;
                }
                if (item.getTitle().equals(VENDOR_QUOTE_CHECKLIST_TITLE) && !item.isDone()) {
                    missingQuote = true;
                }
            }
        }
        if (missingMaf && missingQuote) {
            return "Missing MAF and vendor quote";
        }
        if (missingMaf) {
            return "Missing MAF";
        }
        if (missingQuote) {
            return "Missing vendor quote";
        }
        return null;
    }
}
