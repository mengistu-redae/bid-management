package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.domain.enums.ShipmentMode;

import java.util.UUID;

/** The dimensions a StageTarget override can narrow on, for one entity instance. Any field may be null (not known/applicable). */
public record TargetContext(UUID divisionId, Sector sector, String oemName, ShipmentMode shipmentMode) {

    public static final TargetContext EMPTY = new TargetContext(null, null, null, null);

    public static TargetContext ofDivision(UUID divisionId) {
        return new TargetContext(divisionId, null, null, null);
    }

    public static TargetContext ofDivisionAndSector(UUID divisionId, Sector sector) {
        return new TargetContext(divisionId, sector, null, null);
    }
}
