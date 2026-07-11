package com.pfe.stock_management.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    @Builder.Default
    private List<MaterialQuantityBreakdownDto> quantityBreakdown = new ArrayList<>();

    private String topTechnicianId;
    private String topTechnicianName;
    private int topTechnicianConsumptions;

    private int casesWithoutStockDeclaration;
}
