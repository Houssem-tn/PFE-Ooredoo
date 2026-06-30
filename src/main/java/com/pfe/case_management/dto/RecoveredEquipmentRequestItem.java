package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * A single piece of equipment a technician declares as recovered from the client during a
 * RES (résiliation) intervention. {@code condition} is a free string (GOOD / DAMAGED / ...)
 * mapped to the material-return condition enum when the linked return is created.
 */
public record RecoveredEquipmentRequestItem(
        @NotBlank String equipmentId,
        String equipmentName,
        String unit,
        @Positive int quantity,
        String serialNumber,
        String macAddress,
        String imei,
        String condition
) {
}
