package com.pfe.stock_management.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseConsumptionSummaryDto {

    private int totalConsumptions;
    private int totalItemTypes;
    private int totalQuantityConsumed;
    private int cableTotalMeters;
    private int totalBoxesUsed;

    private String topTechnicianId;
    private String topTechnicianName;
    private int topTechnicianConsumptions;

    private int casesWithoutStockDeclaration;
}
