package com.motiengineering.bidmgmt.importer;

import com.motiengineering.bidmgmt.domain.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every case here is a real value copied from the two sample workbooks
 * (docs/Upcoming Bid Oct 07.xlsx, docs/on hand bid.xlsx) - these aren't
 * synthetic edge cases, they're what the spreadsheets actually contain.
 */
class ParsingUtilsTest {

    @Test
    void parsesValidityDaysDespiteInconsistentSpacingAndCase() {
        assertThat(ParsingUtils.parseValidityDays("150 days ")).isEqualTo(150);
        assertThat(ParsingUtils.parseValidityDays("90 Days ")).isEqualTo(90);
        assertThat(ParsingUtils.parseValidityDays("  90 days. ")).isEqualTo(90);
        assertThat(ParsingUtils.parseValidityDays("118 Days ")).isEqualTo(118);
        assertThat(ParsingUtils.parseValidityDays(null)).isNull();
        assertThat(ParsingUtils.parseValidityDays("unknown")).isNull();
    }

    @Test
    void parsesDealSizeTextIntoEtbByDefault() {
        ParsedMoney m = ParsingUtils.parseDealSize("100 million");
        assertThat(m.needsReview()).isFalse();
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal(100_000_000));
        assertThat(m.currency()).isEqualTo(Currency.ETB);
    }

    @Test
    void parsesBillionDealSize() {
        ParsedMoney m = ParsingUtils.parseDealSize("4 billion");
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal(4_000_000_000L));
    }

    @Test
    void dealSizeWithNoNumberIsFlaggedNotGuessed() {
        ParsedMoney m = ParsingUtils.parseDealSize("TBD");
        assertThat(m.needsReview()).isTrue();
        assertThat(m.amount()).isNull();
    }

    @Test
    void splitsMultiLotBondAmountsByRomanOrDigitLabel() {
        List<ParsingUtils.LotAmount> lots = ParsingUtils.splitLotBondAmounts("Lot I 1640000\nLot-II 820,000");
        assertThat(lots).hasSize(2);
        assertThat(lots.get(0).money().amount()).isEqualByComparingTo(new BigDecimal(1_640_000));
        assertThat(lots.get(1).money().amount()).isEqualByComparingTo(new BigDecimal(820_000));
    }

    @Test
    void ambiguousBondAmountWithNoLotMarkerIsFlagged() {
        List<ParsingUtils.LotAmount> lots = ParsingUtils.splitLotBondAmounts("USD 1 10.000.00");
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).money().needsReview()).isTrue();
    }

    @Test
    void parsesTimeTextIncludingTheRealDoubleColonTypo() {
        assertThat(ParsingUtils.parseTimeText(" 2:00 PM")).isEqualTo(LocalTime.of(14, 0));
        assertThat(ParsingUtils.parseTimeText("10:45AM,")).isEqualTo(LocalTime.of(10, 45));
        assertThat(ParsingUtils.parseTimeText("10::30AM")).isEqualTo(LocalTime.of(10, 30));
        assertThat(ParsingUtils.parseTimeText(" 10:30 AM ")).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void splitsMultipleAccountOfficerNames() {
        assertThat(ParsingUtils.splitNames("Zufan/kaleab")).containsExactly("Zufan", "kaleab");
        assertThat(ParsingUtils.splitNames("Berhanu&Betty&Eyerus")).containsExactly("Berhanu", "Betty", "Eyerus");
        assertThat(ParsingUtils.splitNames("Selam/Tezana")).containsExactly("Selam", "Tezana");
    }

    @Test
    void extractsReferenceNumberEvenWhenGluedToThePrecedingWord() {
        ParsingUtils.RefExtraction r = ParsingUtils.extractReferenceNumber(
                "Supply, Installation and Commissioning of Network Devices, Security Devices & Tools Refreshment Projectrefno:-EEU/Dist./lCB-004/2026/27");
        assertThat(r.found()).isTrue();
        assertThat(r.referenceNumber()).isEqualTo("EEU/Dist./lCB-004/2026/27");
        assertThat(r.cleanTitle()).doesNotContain("refno");
    }

    @Test
    void extractsReferenceNumberWithDashColonSeparator() {
        ParsingUtils.RefExtraction r = ParsingUtils.extractReferenceNumber("Procurement of Smart Pos Device With Application REF NO:-05/2026/27");
        assertThat(r.referenceNumber()).isEqualTo("05/2026/27");
        assertThat(r.cleanTitle()).isEqualTo("Procurement of Smart Pos Device With Application");
    }

    @Test
    void titleWithNoReferenceNumberAtAllIsFlagged() {
        ParsingUtils.RefExtraction r = ParsingUtils.extractReferenceNumber(
                "Procurement of Spare Part Stocks for Biometric Registration Kit Components for Repair and Replacement");
        assertThat(r.found()).isFalse();
        assertThat(r.referenceNumber()).isNull();
    }

    @Test
    void parsesFlexibleDatesWithAndWithoutAnExplicitYear() {
        assertThat(ParsingUtils.parseFlexibleDate("Oct-1", 2026)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ParsingUtils.parseFlexibleDate("August 05", 2026)).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(ParsingUtils.parseFlexibleDate("oct-9-26", 2026)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(ParsingUtils.parseFlexibleDate(" Sep 30, 2026 ", 2020)).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void parsesScoutNoteWithSingleAndMultipleNames() {
        ParsingUtils.ScoutNote single = ParsingUtils.parseScoutNote("Oct-1-(Nardos )", 2026);
        assertThat(single.scoutedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(single.scoutNames()).containsExactly("Nardos");

        ParsingUtils.ScoutNote multi = ParsingUtils.parseScoutNote("Sep 29 (Berhanu&Betty&Eyerus)", 2026);
        assertThat(multi.scoutedDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(multi.scoutNames()).containsExactly("Berhanu", "Betty", "Eyerus");
    }

    @Test
    void extractsLotNumberFromRomanOrDigitLabels() {
        assertThat(ParsingUtils.extractLotNumber("Lot I")).isEqualTo(1);
        assertThat(ParsingUtils.extractLotNumber("LOT-1")).isEqualTo(1);
        assertThat(ParsingUtils.extractLotNumber("Lot-II")).isEqualTo(2);
        assertThat(ParsingUtils.extractLotNumber("Modular Spine Switch(LOT-IV)")).isEqualTo(4);
        assertThat(ParsingUtils.extractLotNumber("No lot mentioned here")).isNull();
    }
}
