package com.motiengineering.bidmgmt.util;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.ChecklistItem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shared "closing soon and still missing its MAF or vendor quote" check - originally dashboard-only, now also surfaced on the plain bid list and the dashboard's readiness-gap table. */
public final class BidRiskEvaluator {

    private static final String MAF_CHECKLIST_TITLE = "MAF from OEM";
    private static final String VENDOR_QUOTE_CHECKLIST_TITLE = "Vendor/distributor quotation";
    public static final int RED_FLAG_WINDOW_DAYS = 5;

    private BidRiskEvaluator() {
    }

    /** True if any lot still has an undone "MAF from OEM" checklist item, regardless of closing date. */
    public static boolean missingMaf(Bid bid) {
        return hasUndoneItem(bid, MAF_CHECKLIST_TITLE);
    }

    /** True if any lot still has an undone "Vendor/distributor quotation" checklist item, regardless of closing date. */
    public static boolean missingVendorQuote(Bid bid) {
        return hasUndoneItem(bid, VENDOR_QUOTE_CHECKLIST_TITLE);
    }

    private static boolean hasUndoneItem(Bid bid, String title) {
        for (BidLot lot : bid.getLots()) {
            for (ChecklistItem item : lot.getChecklistItems()) {
                if (item.getTitle().equals(title) && !item.isDone()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Null unless the bid is open, closing within RED_FLAG_WINDOW_DAYS of now, and still missing its MAF and/or vendor quote checklist item. */
    public static String redFlagReason(Bid bid, Instant now) {
        if (bid.getClosingAt() == null || bid.getStatus().isTerminal()) {
            return null;
        }
        if (!bid.getClosingAt().isBefore(now.plus(RED_FLAG_WINDOW_DAYS, ChronoUnit.DAYS))) {
            return null;
        }
        boolean missingMaf = missingMaf(bid);
        boolean missingQuote = missingVendorQuote(bid);
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
