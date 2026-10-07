package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.dto.ActivityDigestRowDto;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.dto.StatusChangeDigestRowDto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Plain string-built HTML - no template engine, consistent with "keep the UI clean and plain" for a handful of email shapes. */
final class EmailTemplates {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private EmailTemplates() {
    }

    static EmailContent bidClosingReminder(Bid bid, int daysOut, String appBaseUrl) {
        String subject = String.format("Closing in %d day%s: %s - %s", daysOut, daysOut == 1 ? "" : "s", bid.getOrganization().getName(), bid.getTitle());
        String html = wrap(
                "<h2>Bid closing in " + daysOut + " day" + (daysOut == 1 ? "" : "s") + "</h2>" +
                        "<p><strong>" + escape(bid.getTitle()) + "</strong><br>" +
                        escape(bid.getOrganization().getName()) + (bid.getReferenceNumber() != null ? " &middot; Ref: " + escape(bid.getReferenceNumber()) : "") + "</p>" +
                        "<p>Closing: <strong>" + formatDateTime(bid.getClosingAt()) + "</strong></p>" +
                        link(appBaseUrl, "/bids/" + bid.getId(), "View bid"));
        return new EmailContent(subject, html);
    }

    static EmailContent clarificationReminder(Bid bid, String appBaseUrl) {
        String subject = "Clarification deadline in 2 days: " + bid.getOrganization().getName() + " - " + bid.getTitle();
        String html = wrap(
                "<h2>Clarification deadline in 2 days</h2>" +
                        "<p><strong>" + escape(bid.getTitle()) + "</strong><br>" + escape(bid.getOrganization().getName()) + "</p>" +
                        "<p>Clarification deadline: <strong>" + formatDateTime(bid.getClarificationDeadline()) + "</strong></p>" +
                        link(appBaseUrl, "/bids/" + bid.getId(), "View bid"));
        return new EmailContent(subject, html);
    }

    static EmailContent dealRegistrationExpiryReminder(DealRegistration reg, int daysOut, String appBaseUrl) {
        String subject = String.format("Deal registration expiring in %d days: %s - %s", daysOut, reg.getOem().getName(), reg.getOrganization().getName());
        String html = wrap(
                "<h2>Deal registration expiring in " + daysOut + " days</h2>" +
                        "<p><strong>" + escape(reg.getOem().getName()) + "</strong> - " + escape(reg.getOrganization().getName()) + "</p>" +
                        "<p>Expiry: <strong>" + formatDate(reg.getExpiryDate()) + "</strong></p>" +
                        link(appBaseUrl, "/deal-registrations/" + reg.getId(), "View registration"));
        return new EmailContent(subject, html);
    }

    static EmailContent bidBondNotReturnedReminder(Bid bid, BidLot lot, String appBaseUrl) {
        String subject = "Bid bond not yet returned: " + bid.getOrganization().getName() + " - " + bid.getTitle();
        String html = wrap(
                "<h2>Bid bond not yet returned (30+ days since opening)</h2>" +
                        "<p><strong>" + escape(bid.getTitle()) + "</strong><br>" + escape(bid.getOrganization().getName()) + " &middot; " + escape(lot.getLotLabel()) + "</p>" +
                        "<p>Amount: <strong>" + formatMoney(lot.getBidBondAmount(), lot.getBidBondCurrency() == null ? "" : lot.getBidBondCurrency().name()) + "</strong></p>" +
                        "<p>Opened: " + formatDateTime(bid.getOpeningAt()) + "</p>" +
                        link(appBaseUrl, "/bids/" + bid.getId(), "View bid"));
        return new EmailContent(subject, html);
    }

    static EmailContent directorDigest(DashboardDto dashboard, List<ActivityDigestRowDto> activityYesterday, List<StatusChangeDigestRowDto> statusChangesYesterday, String appBaseUrl) {
        StringBuilder body = new StringBuilder();
        body.append("<h2>Daily digest</h2>");

        body.append("<h3>Closing this week</h3>");
        if (dashboard.closingThisWeek().isEmpty()) {
            body.append("<p style=\"color:#666;\">Nothing closing in the next 7 days.</p>");
        } else {
            body.append("<ul>");
            dashboard.closingThisWeek().forEach(r -> body.append("<li>")
                    .append(escape(r.title())).append(" - ").append(escape(r.organizationName()))
                    .append(" (").append(formatDateTime(r.closingAt())).append(")")
                    .append(r.redFlag() ? " <strong style=\"color:#a3231f;\">[" + escape(r.redFlagReason()) + "]</strong>" : "")
                    .append("</li>"));
            body.append("</ul>");
        }

        body.append("<h3>At risk (closing within 5 days, missing MAF or vendor quote)</h3>");
        List<String> atRisk = dashboard.closingThisWeek().stream().filter(r -> r.redFlag())
                .map(r -> r.title() + " - " + r.organizationName() + ": " + r.redFlagReason()).toList();
        if (atRisk.isEmpty()) {
            body.append("<p style=\"color:#666;\">Nothing currently flagged.</p>");
        } else {
            body.append("<ul>");
            atRisk.forEach(s -> body.append("<li>").append(escape(s)).append("</li>"));
            body.append("</ul>");
        }

        body.append("<h3>What changed yesterday</h3>");
        if (statusChangesYesterday.isEmpty() && activityYesterday.isEmpty()) {
            body.append("<p style=\"color:#666;\">No activity yesterday.</p>");
        } else {
            body.append("<ul>");
            statusChangesYesterday.forEach(c -> body.append("<li>")
                    .append(escape(c.bidTitle())).append(": ").append(c.fromStatus() == null ? "-" : c.fromStatus())
                    .append(" &rarr; ").append(c.toStatus())
                    .append(c.changedByName() != null ? " (" + escape(c.changedByName()) + ")" : "")
                    .append("</li>"));
            activityYesterday.forEach(a -> body.append("<li>")
                    .append(a.authorName() != null ? escape(a.authorName()) + ": " : "")
                    .append(escape(a.note())).append("</li>"));
            body.append("</ul>");
        }

        body.append(link(appBaseUrl, "/dashboard", "Open dashboard"));

        return new EmailContent("Daily digest - " + formatDate(java.time.LocalDate.now(ZONE)), wrap(body.toString()));
    }

    private static String wrap(String innerHtml) {
        return "<html><body style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#1b1f24;\">" + innerHtml + "</body></html>";
    }

    private static String link(String appBaseUrl, String path, String label) {
        return "<p><a href=\"" + appBaseUrl + path + "\">" + escape(label) + "</a></p>";
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : DATE_FMT.format(date);
    }

    private static String formatDateTime(Instant instant) {
        return instant == null ? "-" : DATETIME_FMT.format(instant.atZone(ZONE));
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        return (currency == null || currency.isBlank() ? "" : currency + " ") + fmt.format(amount);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
