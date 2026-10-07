package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free-text parsing for the two source spreadsheets. Every method here is
 * intentionally tolerant - the source data has years of accumulated typos
 * (double colons, stray commas, inconsistent "REF NO" spelling) - and
 * surfaces its own confidence via a needsReview flag rather than throwing,
 * so a bad cell degrades to "flagged for manual review" instead of failing
 * the whole import. See ParsingUtilsTest for the concrete cases these
 * patterns were built against.
 */
public final class ParsingUtils {

    private ParsingUtils() {
    }

    private static final Pattern VALIDITY_DAYS = Pattern.compile("(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE);

    public static Integer parseValidityDays(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = VALIDITY_DAYS.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static final Pattern DEAL_SIZE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(million|billion|m|b)?", Pattern.CASE_INSENSITIVE);

    /** "100 million" -> 100,000,000 ETB. "4 billion" -> 4,000,000,000 ETB. "USD ..." switches the currency. */
    public static ParsedMoney parseDealSize(String text) {
        if (text == null || text.isBlank()) {
            return ParsedMoney.empty();
        }
        Currency currency = text.toUpperCase(Locale.ROOT).contains("USD") ? Currency.USD : Currency.ETB;
        Matcher m = DEAL_SIZE.matcher(text);
        if (!m.find()) {
            return ParsedMoney.flagged(text, "Could not parse a numeric deal size");
        }
        BigDecimal base = new BigDecimal(m.group(1).replace(",", ""));
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        BigDecimal multiplier = switch (unit) {
            case "million", "m" -> BigDecimal.valueOf(1_000_000);
            case "billion", "b" -> BigDecimal.valueOf(1_000_000_000L);
            default -> BigDecimal.ONE;
        };
        return ParsedMoney.of(base.multiply(multiplier), currency);
    }

    private static final Pattern LOT_AMOUNT = Pattern.compile("(?i)(lot[\\s-]*[ivx\\d]+)\\s*[:\\-]?\\s*([0-9][0-9,.]*)");

    /**
     * Splits a bid-bond cell that embeds more than one lot's amount, e.g.
     * "Lot I 1640000\nLot-II 820,000". Returns one entry per lot found; a
     * cell with no "Lot ..." marker at all (e.g. "USD 1 10.000.00") comes
     * back as a single flagged entry under a synthetic "Lot 1" label,
     * exactly as the brief asks ("otherwise flag for manual review").
     */
    public static List<LotAmount> splitLotBondAmounts(String text) {
        List<LotAmount> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Currency currency = text.toUpperCase(Locale.ROOT).contains("USD") ? Currency.USD : Currency.ETB;
        Matcher m = LOT_AMOUNT.matcher(text);
        boolean any = false;
        while (m.find()) {
            any = true;
            String label = normalizeLotLabel(m.group(1));
            try {
                BigDecimal amount = new BigDecimal(m.group(2).replace(",", ""));
                result.add(new LotAmount(label, ParsedMoney.of(amount, currency)));
            } catch (NumberFormatException e) {
                result.add(new LotAmount(label, ParsedMoney.flagged(text, "Could not parse lot amount")));
            }
        }
        if (!any) {
            result.add(new LotAmount("Lot 1", ParsedMoney.flagged(text, "Bid bond amount is not a plain number")));
        }
        return result;
    }

    private static String normalizeLotLabel(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }

    private static final Pattern TIME_TEXT = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", Pattern.CASE_INSENSITIVE);

    /** Parses times written as free text: " 2:00 PM", "10:45AM,", "10::30AM" (a real double-colon typo in the source). */
    public static LocalTime parseTimeText(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim().replace("::", ":");
        Matcher m = TIME_TEXT.matcher(cleaned);
        if (!m.find()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        String ampm = m.group(3);
        if (ampm != null) {
            boolean pm = ampm.equalsIgnoreCase("PM");
            if (pm && hour < 12) {
                hour += 12;
            } else if (!pm && hour == 12) {
                hour = 0;
            }
        }
        if (hour > 23 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private static final Pattern NAME_SPLIT = Pattern.compile("[/&,]|(?i:\\s+and\\s+)");

    /** "Zufan/kaleab" -> ["Zufan", "kaleab"]; "Berhanu&Betty&Eyerus" -> ["Berhanu", "Betty", "Eyerus"]. */
    public static List<String> splitNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return names;
        }
        for (String part : NAME_SPLIT.split(text)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    // "ref", optionally followed by '.', whitespace, "no", optionally '.', then
    // any amount of ':'/'-'/whitespace, then the reference number itself to
    // the end of the title string - deliberately no word-boundary requirement
    // before "ref", since the source has titles like "...Projectrefno:-EEU/..."
    // with no space at all before the marker.
    private static final Pattern REF_NUMBER = Pattern.compile("(?i)ref\\.?\\s*no\\.?\\s*[:\\-]*\\s*(.+)", Pattern.DOTALL);

    public static RefExtraction extractReferenceNumber(String rawTitle) {
        if (rawTitle == null) {
            return new RefExtraction("", null, false, true);
        }
        Matcher m = REF_NUMBER.matcher(rawTitle);
        if (!m.find()) {
            String cleanTitle = collapseWhitespace(rawTitle);
            return new RefExtraction(cleanTitle, null, false, true);
        }
        String cleanTitle = collapseWhitespace(rawTitle.substring(0, m.start()));
        String refNumber = collapseWhitespace(m.group(1)).replaceAll("[.\\s]+$", "");
        boolean needsReview = refNumber.isBlank() || refNumber.length() > 60 || refNumber.split("\\s+").length > 5;
        return new RefExtraction(cleanTitle, refNumber.isBlank() ? null : refNumber, true, needsReview);
    }

    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private static final Pattern FLEXIBLE_DATE = Pattern.compile("([A-Za-z]{3,9})[\\-\\s]+(\\d{1,2})(?:[,\\-\\s]+(\\d{2,4}))?");

    /**
     * Parses dates written as "Oct-1", "August 05", "Sep 29, 2026", "oct-9-26"
     * - the formats seen in the "Date &amp; Name" and text-valued
     * Clarification Date columns. When the text carries no year, {@code
     * defaultYear} is used (the scouting/clarification date is always close
     * to the tender's own closing year).
     */
    public static LocalDate parseFlexibleDate(String text, int defaultYear) {
        if (text == null) {
            return null;
        }
        Matcher m = FLEXIBLE_DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        String monthKey = m.group(1).substring(0, Math.min(3, m.group(1).length())).toLowerCase(Locale.ROOT);
        Integer month = MONTHS.get(monthKey);
        if (month == null) {
            return null;
        }
        int day = Integer.parseInt(m.group(2));
        int year = defaultYear;
        if (m.group(3) != null) {
            int parsedYear = Integer.parseInt(m.group(3));
            year = parsedYear < 100 ? 2000 + parsedYear : parsedYear;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]*)\\)");

    /** "Oct-1-(Nardos )" / "Sep 29 (Berhanu&Betty&Eyerus)" - scouted date + scout name(s). */
    public static ScoutNote parseScoutNote(String text, int defaultYear) {
        if (text == null || text.isBlank()) {
            return new ScoutNote(null, List.of());
        }
        Matcher paren = PARENTHETICAL.matcher(text);
        List<String> names = List.of();
        String datePart = text;
        if (paren.find()) {
            names = splitNames(paren.group(1));
            datePart = text.substring(0, paren.start());
        }
        LocalDate date = parseFlexibleDate(datePart, defaultYear);
        return new ScoutNote(date, names);
    }

    private static final Pattern LOT_NUMBER = Pattern.compile("(?i)lot[\\s-]*([ivx\\d]+)");
    private static final Map<String, Integer> ROMAN = Map.of(
            "i", 1, "ii", 2, "iii", 3, "iv", 4, "v", 5, "vi", 6, "vii", 7, "viii", 8, "ix", 9, "x", 10);

    /**
     * Pulls a lot number out of a label like "Lot I", "LOT-1", "Lot-II" so
     * lots can be matched across the two files despite one using roman
     * numerals and the other plain digits for the same lot.
     */
    public static Integer extractLotNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = LOT_NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        String token = m.group(1).toLowerCase(Locale.ROOT);
        if (token.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(token);
        }
        return ROMAN.get(token);
    }

    public record LotAmount(String label, ParsedMoney money) {
    }

    public record RefExtraction(String cleanTitle, String referenceNumber, boolean found, boolean needsReview) {
    }

    public record ScoutNote(LocalDate scoutedDate, List<String> scoutNames) {
    }
}
