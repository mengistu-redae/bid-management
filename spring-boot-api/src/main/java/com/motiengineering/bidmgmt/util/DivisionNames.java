package com.motiengineering.bidmgmt.util;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Division;

import java.util.LinkedHashSet;
import java.util.List;

/** Distinct division names across a bid's lots, in first-seen order - shared by every dashboard row DTO that tags or groups bids by division. */
public final class DivisionNames {

    private DivisionNames() {
    }

    public static List<String> distinct(Bid bid) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (BidLot lot : bid.getLots()) {
            Division division = lot.getDivision();
            if (division != null) {
                names.add(division.getName());
            }
        }
        return List.copyOf(names);
    }
}
