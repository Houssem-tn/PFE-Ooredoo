package com.pfe.stock_management.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-TECH rollup for one DPM, built from automatic source allocations.
 * Powers the "TECH DISTRIBUTIONS" section of the DPM detail page:
 * distributed / used / returned / remaining for each TECH that received
 * material sourced from this DPM.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmTechRollupDto {

    private String technicianId;
    private String technicianName;

    @Builder.Default
    private List<Item> items = new ArrayList<>();

    private int totalDistributed;
    private int totalUsed;
    private int totalReturned;
    private int totalRemaining;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String equipmentId;
        private String equipmentName;
        private String unit;
        private int distributedQuantity;
        private int usedQuantity;
        private int returnedQuantity;
        private int remainingQuantity;
    }
}
