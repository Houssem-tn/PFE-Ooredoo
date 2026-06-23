package com.pfe.case_management.dto;

import lombok.*;

import java.time.Instant;

/**
 * One material line consumed by the technician during an intervention.
 * Read-only projection joined from {@code case_stock_consumptions} + {@code stock_equipment}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumedMaterialDto {

    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String category;
    private int quantity;
    private String unit;
    private Instant consumedAt;
    private String consumedByTechName;

    /** Optional stock snapshot context. */
    private Integer stockBefore;
    private Integer stockAfter;
}
