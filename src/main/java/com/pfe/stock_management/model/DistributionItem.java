package com.pfe.stock_management.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributionItem {

    private String equipmentId;
    private String equipmentName;
    private String unit;
    private int quantity;

    /** Tracked-unit ids moved with this line (null/empty for quantity-based items). */
    private List<String> instanceIds;

    /** Denormalised IMEI/Serial of the moved units, for display & audit without a lookup. */
    private List<TrackedUnitRef> trackedUnits;
}
