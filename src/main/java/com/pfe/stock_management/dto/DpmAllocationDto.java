package com.pfe.stock_management.dto;

import lombok.*;

import java.time.Instant;

/**
 * Flat view of a single {@code DpmAllocation} for the DPM detail page and the
 * per-distribution source breakdown.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmAllocationDto {

    private String id;
    private String distributionId;
    private String dpmId;
    private String dpmNumber;
    private String sttId;
    private String technicianId;
    private String technicianName;
    private String equipmentId;
    private String equipmentName;
    private String unit;
    private int allocatedQuantity;
    private int sourceBalanceBefore;
    private int sourceBalanceAfter;
    private Instant allocatedAt;

    /** Raw distribution status (PENDING_TECH_CONFIRMATION / CONFIRMED_BY_TECH / CANCELLED). */
    private String distributionStatus;
}
