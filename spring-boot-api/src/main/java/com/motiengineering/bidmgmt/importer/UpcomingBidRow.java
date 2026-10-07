package com.motiengineering.bidmgmt.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One parsed row from "Upcoming Bid *.xlsx" - a single scouted tender, not yet split into lots. */
public class UpcomingBidRow {

    public int sourceRowNumber;
    public String organizationRaw;
    public String cleanTitle;
    public String referenceNumber;
    public LocalDateTime closingAt;
    public LocalDateTime openingAt;
    public Integer bidValidityDays;
    public Integer bidBondValidityDays;
    public List<ParsingUtils.LotAmount> bidBondLots = new ArrayList<>();
    public LocalDate clarificationDeadline;
    public LocalDate scoutedDate;
    public List<String> scoutNames = new ArrayList<>();
    public String inferredDivisionCode;
    public final List<String> warnings = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
    }
}
