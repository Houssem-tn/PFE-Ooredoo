package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * A single piece of equipment a technician declares as recovered from the client during a PLT
 * (complaint) or RES (résiliation) intervention. {@code condition} is a free string (GOOD / DAMAGED
 * / ...) mapped to {@code ReturnCondition}. {@code disposition} is a free string (RETRIEVED /
 * DAMAGED / MISSING / NOT_FOUND / ALREADY_RETURNED) mapped to {@code RecoveryDisposition} — both
 * stay plain strings here so {@code case_management} doesn't take a compile dependency on the
 * {@code stock_management.retrieved_stock} enums; null/blank defaults to RETRIEVED (Complaint lines
 * never send one, since only Résiliation exposes the disposition picker).
 */
public record RecoveredEquipmentRequestItem(
        @NotBlank String equipmentId,
        String equipmentName,
        String unit,
        @Positive int quantity,
        String serialNumber,
        String macAddress,
        String imei,
        String condition,
        String disposition,
        String dispositionNote
) {
}
