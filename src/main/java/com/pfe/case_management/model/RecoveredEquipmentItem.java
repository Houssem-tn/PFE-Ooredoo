package com.pfe.case_management.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equipment retrieved from a client during a RES (résiliation) intervention. Unlike
 * {@link UsedEquipmentItem} this is NOT consumed from TECH stock — it is declared as recovered
 * client material and fed into the material-return workflow (STT → ZM/depot handover).
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
    /** Material-return code this recovery was linked to, for traceability. */
    private String returnCode;
    /** Linked tracked-unit id when the recovered unit is an EquipmentInstance. */
    private String instanceId;
}
