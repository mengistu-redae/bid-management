package com.motiengineering.bidmgmt.importer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One parsed row from "Issues *.xlsx" - either an operational issue (dated weekly sheet) or a contract-closure issue ("Project issuse" sheet). */
public class IssueWorkbookRow {

    public String sheetName;
    public int sourceRowNumber;
    /** "OPERATIONAL" or "CONTRACT_CLOSURE" - matches IssueCategory.name(), kept as a plain String so this module doesn't need a domain-layer dependency. */
    public String category;
    /** Raw section heading from the sheet (e.g. "N&S Division") - best-fit mapped to a Division code by the caller, not here; null for CONTRACT_CLOSURE rows (the sheet has no division grouping). */
    public String divisionSectionLabel;
    /** Set by ImportRowEditor when the user picks a division explicitly in the preview screen, overriding the best-fit guess from divisionSectionLabel. */
    public String overrideDivisionCode;
    public String organizationRaw;
    public String itemOrSystem;
    public String description;
    public String currentStatusNote;
    public String nextAction;
    public String challenge;
    /** Contract signing date, for CONTRACT_CLOSURE rows only - used as the issue's openedAt. */
    public LocalDate referenceDate;
    public final List<String> warnings = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
    }
}
