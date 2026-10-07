package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.dto.ImportRowEditDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the three things a per-row edit can do: correct a field, skip a row, or (most importantly) leave everything else alone. */
class ImportRowEditorTest {

    private UpcomingBidRow upcomingRow(String org, String title) {
        UpcomingBidRow row = new UpcomingBidRow();
        row.organizationRaw = org;
        row.cleanTitle = title;
        row.inferredDivisionCode = "NETWORK";
        return row;
    }

    @Test
    void noEditsLeavesEveryRowUnchanged() {
        List<UpcomingBidRow> rows = List.of(upcomingRow("CBE", "Title A"), upcomingRow("Awash", "Title B"));

        List<UpcomingBidRow> result = ImportRowEditor.applyToUpcoming(rows, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).organizationRaw).isEqualTo("CBE");
        assertThat(result.get(1).organizationRaw).isEqualTo("Awash");
    }

    @Test
    void editsOnlyOverrideTheFieldsTheyActuallySupply() {
        List<UpcomingBidRow> rows = List.of(upcomingRow("CBE Garbled", "Title A"));
        ImportRowEditDto edit = new ImportRowEditDto(0, "Commercial Bank of Ethiopia", null, null, false);

        List<UpcomingBidRow> result = ImportRowEditor.applyToUpcoming(rows, List.of(edit));

        assertThat(result.get(0).organizationRaw).isEqualTo("Commercial Bank of Ethiopia");
        assertThat(result.get(0).cleanTitle).isEqualTo("Title A");
        assertThat(result.get(0).inferredDivisionCode).isEqualTo("NETWORK");
    }

    @Test
    void blankEditFieldsNeverClobberTheParsedValue() {
        List<UpcomingBidRow> rows = List.of(upcomingRow("CBE", "Title A"));
        ImportRowEditDto edit = new ImportRowEditDto(0, "   ", "", null, false);

        List<UpcomingBidRow> result = ImportRowEditor.applyToUpcoming(rows, List.of(edit));

        assertThat(result.get(0).organizationRaw).isEqualTo("CBE");
        assertThat(result.get(0).cleanTitle).isEqualTo("Title A");
    }

    @Test
    void skippedRowsAreDroppedEntirelyAndEveryoneElseKeepsTheirOriginalIndex() {
        List<UpcomingBidRow> rows = List.of(upcomingRow("A", "1"), upcomingRow("B", "2"), upcomingRow("C", "3"));
        ImportRowEditDto skipMiddle = new ImportRowEditDto(1, null, null, null, true);

        List<UpcomingBidRow> result = ImportRowEditor.applyToUpcoming(rows, List.of(skipMiddle));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).organizationRaw).isEqualTo("A");
        assertThat(result.get(1).organizationRaw).isEqualTo("C");
    }

    @Test
    void countSkippedCountsOnlyEditsWithSkipTrue() {
        List<ImportRowEditDto> edits = List.of(
                new ImportRowEditDto(0, null, null, null, true),
                new ImportRowEditDto(1, "fix", null, null, false),
                new ImportRowEditDto(2, null, null, null, true));

        assertThat(ImportRowEditor.countSkipped(edits)).isEqualTo(2);
        assertThat(ImportRowEditor.countSkipped(null)).isZero();
    }

    private BidsTrackerRow trackerRow(String org, String division) {
        BidsTrackerRow row = new BidsTrackerRow();
        row.organizationRaw = org;
        row.inferredDivisionCode = division;
        return row;
    }

    @Test
    void trackerEditsApplyTheSameWayAsUpcomingOnes() {
        List<BidsTrackerRow> rows = List.of(trackerRow("Garbled Name", null));
        ImportRowEditDto edit = new ImportRowEditDto(0, "Clean Name", null, "SECURITY", false);

        List<BidsTrackerRow> result = ImportRowEditor.applyToTracker(rows, List.of(edit));

        assertThat(result.get(0).organizationRaw).isEqualTo("Clean Name");
        assertThat(result.get(0).inferredDivisionCode).isEqualTo("SECURITY");
    }
}
