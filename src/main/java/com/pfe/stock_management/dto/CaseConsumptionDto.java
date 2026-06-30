package com.pfe.stock_management.dto;

import com.pfe.case_management.enums.CaseType;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseConsumptionDto {

    private String id;
    private String caseId;
    private String crmCase;
    private CaseType caseType;

    /** ACT_CONSUMPTION vs PLT_USAGE_SWAP. */
    private String usageContext;

    private String technicianId;
    private String technicianName;

    private String sttId;
    private String sttName;

    private String zmId;
    private String zmName;

    private List<ConsumedEquipmentItem> consumedItems;
    private int totalQuantityUsed;
    private int totalItemTypes;

    private Instant consumedAt;
    private String notes;
}
