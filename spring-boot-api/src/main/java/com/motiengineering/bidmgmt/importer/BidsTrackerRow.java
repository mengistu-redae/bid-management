package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.ScopeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** One parsed row from "on hand bid.xlsx" (any of its four sheets). */
public class BidsTrackerRow {

    public String sheetName;
    public int sourceRowNumber;
    public BidStatus sheetStatus;
    public String organizationRaw;
    public String cleanTitle;
    public String referenceNumber;
    public Set<ScopeType> scopeTypes = EnumSet.noneOf(ScopeType.class);
    public ParsedMoney dealSize = ParsedMoney.empty();
    public String statusNote;
    public LocalDateTime submissionDeadline;
    public List<String> accountOfficerNames = new ArrayList<>();
    public String extraNotes;
    public String inferredDivisionCode;
    public final List<String> warnings = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
    }
}
