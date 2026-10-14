package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.dto.ActivityDigestRowDto;
import com.motiengineering.bidmgmt.dto.AlertItemDto;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.dto.StatusChangeDigestRowDto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    static EmailContent delayAlert(IndicatorLevel level, AlertItemDto item, Double percentUsed, String appBaseUrl) {
        String subject = level + ": " + item.title() + (item.subtitle() != null ? " - " + item.subtitle() : "");
        String html = wrap(
                "<h2>" + level + "</h2>" +
                        "<p><strong>" + escape(item.title()) + "</strong>" + (item.subtitle() != null ? "<br>" + escape(item.subtitle()) : "") + "</p>" +
                        (percentUsed != null ? "<p>" + Math.round(percentUsed) + "% of target used</p>" : "") +
                        (item.extra() != null ? "<p>" + escape(item.extra()) + "</p>" : "") +
                        link(appBaseUrl, item.linkPath(), "View"));
        return new EmailContent(subject, html);
    }

    static EmailContent staleAlert(AlertItemDto item, boolean doubleStale, String appBaseUrl) {
        String subject = (doubleStale ? "Still stale: " : "Stale: ") + item.title();
        String html = wrap(
                "<h2>" + (doubleStale ? "Still no activity" : "No recent activity") + "</h2>" +
                        "<p><strong>" + escape(item.title()) + "</strong>" + (item.subtitle() != null ? "<br>" + escape(item.subtitle()) : "") + "</p>" +
                        link(appBaseUrl, item.linkPath(), "View"));
        return new EmailContent(subject, html);
    }

    static EmailContent followUpDueAlert(AlertItemDto item, LocalDate dueDate, String appBaseUrl) {
        String subject = "Follow-up due: " + item.title();
        String html = wrap(
                "<h2>Follow-up due" + (dueDate != null ? " (" + formatDate(dueDate) + ")" : "") + "</h2>" +
                        "<p><strong>" + escape(item.title()) + "</strong>" + (item.subtitle() != null ? "<br>" + escape(item.subtitle()) : "") + "</p>" +
                        link(appBaseUrl, item.linkPath(), "View"));
        return new EmailContent(subject, html);
    }

    static EmailContent keyDateChangedAlert(AlertItemDto item, String fieldName, String oldValue, String newValue, String appBaseUrl) {
        String subject = "Date changed (" + fieldName + "): " + item.title();
        String html = wrap(
                "<h2>Key date changed</h2>" +
                        "<p><strong>" + escape(item.title()) + "</strong>" + (item.subtitle() != null ? "<br>" + escape(item.subtitle()) : "") + "</p>" +
                        "<p>" + escape(fieldName) + ": " + escape(oldValue) + " &rarr; <strong>" + escape(newValue) + "</strong></p>" +
                        link(appBaseUrl, item.linkPath(), "View"));
        return new EmailContent(subject, html);
    }

    static EmailContent userDailyDigest(String userName, List<AlertItemDto> dueFollowUps, List<AlertItemDto> newlyAmberRed,
                                         List<AlertItemDto> staleItems, Integer criticalCountForDirector, String appBaseUrl) {
        StringBuilder body = new StringBuilder();
        body.append("<h2>Daily digest for ").append(escape(userName)).append("</h2>");

        if (criticalCountForDirector != null) {
            body.append("<p><strong>").append(criticalCountForDirector).append(" item(s) currently critical across the pipeline.</strong></p>");
        }

        body.append("<h3>Follow-ups due</h3>");
        body.append(itemList(dueFollowUps, "Nothing due."));

        body.append("<h3>Newly amber/red</h3>");
        body.append(itemList(newlyAmberRed, "Nothing newly flagged."));

        body.append("<h3>Stale (no recent activity)</h3>");
        body.append(itemList(staleItems, "Nothing stale."));

        body.append(link(appBaseUrl, "/delays", "Open the Delays board"));
        return new EmailContent("Daily digest - " + formatDate(LocalDate.now(ZONE)), wrap(body.toString()));
    }

    static EmailContent weeklyBottleneckSummary(List<AlertItemDto> topDelayed, Map<String, Long> stageRedCounts, Map<String, Long> waitingOnTotals, String appBaseUrl) {
        StringBuilder body = new StringBuilder();
        body.append("<h2>Weekly bottleneck summary</h2>");

        body.append("<h3>Top delayed records</h3>");
        body.append(itemList(topDelayed, "Nothing currently delayed."));

        body.append("<h3>Stages with the most red/critical items</h3>");
        if (stageRedCounts.isEmpty()) {
            body.append("<p style=\"color:#666;\">None.</p>");
        } else {
            body.append("<ul>");
            stageRedCounts.forEach((stage, count) -> body.append("<li>").append(escape(stage)).append(": ").append(count).append("</li>"));
            body.append("</ul>");
        }

        body.append("<h3>Who we're waiting on most</h3>");
        if (waitingOnTotals.isEmpty()) {
            body.append("<p style=\"color:#666;\">No waiting-on data yet.</p>");
        } else {
            body.append("<ul>");
            waitingOnTotals.forEach((party, count) -> body.append("<li>").append(escape(party)).append(": ").append(count).append(" open item(s)</li>"));
            body.append("</ul>");
        }

        body.append(link(appBaseUrl, "/delays", "Open the Delays board"));
        return new EmailContent("Weekly bottleneck summary - " + formatDate(LocalDate.now(ZONE)), wrap(body.toString()));
    }

    private static String itemList(List<AlertItemDto> items, String emptyMessage) {
        if (items.isEmpty()) {
            return "<p style=\"color:#666;\">" + emptyMessage + "</p>";
        }
        StringBuilder sb = new StringBuilder("<ul>");
        items.forEach(i -> sb.append("<li>")
                .append(escape(i.title()))
                .append(i.subtitle() != null ? " - " + escape(i.subtitle()) : "")
                .append(i.extra() != null ? " (" + escape(i.extra()) + ")" : "")
                .append("</li>"));
        sb.append("</ul>");
        return sb.toString();
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
