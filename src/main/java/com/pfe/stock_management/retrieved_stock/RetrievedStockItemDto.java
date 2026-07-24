package com.pfe.stock_management.retrieved_stock;

import lombok.*;

import java.time.Instant;

/** Read projection of a {@link RetrievedStockItem} for TECH / ZM / ADMIN views. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedStockItemDto {

    private String id;

    private String caseId;
    private String crmCase;
    private String caseUsageContext;
    private String clientName;
    private String clientPhone;

    private String technicianId;
    private String technicianName;
    private String sttId;
    private String sttName;
    private String zmId;
    private String zmName;

    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String unit;
    private int quantity;
    private String serialNumber;
    private String macAddress;
    private String imei;
    private String instanceId;

    private String disposition;
    private String condition;
    private String dispositionNote;

    private String proofFileId;
    private String proofViewUrl;
    private String proofDownloadUrl;

    private String status;
    private Instant retrievedAt;
    private String declaredBy;

    // Correct collector (STT). Legacy ZM fields kept for historical documents.
    private String collectedBySttId;
    private String collectedBySttName;
    private String collectedByZmId;
    private String collectedByZmName;
    private Instant collectedAt;
    private String collectionNote;

    // Material Return integration
    private String materialReturnRequestId;
    private Instant addedToReturnAt;
    private Instant returnedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
