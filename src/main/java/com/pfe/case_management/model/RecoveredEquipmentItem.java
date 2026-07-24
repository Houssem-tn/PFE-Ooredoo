package com.pfe.case_management.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equipment retrieved from a client during a PLT (complaint) or RES (résiliation) intervention.
 * Unlike {@link UsedEquipmentItem} this is NOT consumed from TECH stock — it is declared as
 * recovered client material and fed into the technician Retrieved-Stock → ZM collection workflow
 * ({@code RetrievedStockService}). {@code returnCode}/{@code returnId}-shaped fields are absent by
 * design here: unlike the legacy RES-only path, this workflow has no {@code MaterialReturnRequest}
 * involved; see {@code retrievedStockItemId} instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveredEquipmentItem {
    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String unit;
    private int quantity;
    private String serialNumber;
    private String macAddress;
    private String imei;
    /** GOOD / DAMAGED / BROKEN / ... — physical condition of the recovered unit. */
    private String condition;
    /** Legacy material-return code this recovery was linked to (older RES-only path only). */
    private String returnCode;
    /** Linked tracked-unit id when the recovered unit is an EquipmentInstance. */
    private String instanceId;
    /** RETRIEVED / DAMAGED / MISSING / NOT_FOUND / ALREADY_RETURNED — what actually happened. */
    private String disposition;
    private String dispositionNote;
    /**
     * Linked {@code RetrievedStockItem} id, present when {@code disposition} is physically-held
     * (RETRIEVED/DAMAGED). Null for MISSING/NOT_FOUND/ALREADY_RETURNED lines and for rows declared
     * through the older RES-only material-return path.
     */
    private String retrievedStockItemId;
}
