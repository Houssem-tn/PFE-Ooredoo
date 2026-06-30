package com.pfe.stock_management.entity;

import com.pfe.case_management.enums.CaseType;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "case_stock_consumptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseStockConsumption {

    @Id
    private String id;

    @Indexed(unique = true)
    private String caseId;

    private String crmCase;
    private CaseType caseType;

    /** Distinguishes ACT_CONSUMPTION vs PLT_USAGE_SWAP for audit clarity. */
    private String usageContext;

    private String technicianId;
    private String technicianName;

    private String sttId;
    private String sttName;

    private String zmId;
    private String zmName;

    private List<ConsumedEquipmentItem> consumedItems;

    private Instant consumedAt;
    private String createdBy;
    private String notes;
}
