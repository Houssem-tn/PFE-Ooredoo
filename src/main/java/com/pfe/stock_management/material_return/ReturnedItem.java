package com.pfe.stock_management.material_return;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnedItem {

    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String description;
    private String unit;
    private int quantity;

    // Optional identifiers — not enforced in MVP
    private String serialNumber;
    private String macAddress;
    private String imei;

    /** Linked tracked-unit id when this returned item corresponds to an EquipmentInstance. */
    private String instanceId;

    private ReturnCondition condition;

    // Optional link back to the STT Collected Stock record this line came from.
    private String retrievedStockItemId;
    private String originalTechnicianId;
    private String originalTechnicianName;
    private String originalTechnicianEmail;
    private String originalCaseId;
    private String originalCaseReference;
    private String originalCaseType;
    private String originalCustomerName;
    private String originalCustomerPhone;
    private Instant originalRecoveryDate;
    private Instant sttCollectedAt;
    private String proofFileId;
    private String proofViewUrl;
    private String proofDownloadUrl;
}
