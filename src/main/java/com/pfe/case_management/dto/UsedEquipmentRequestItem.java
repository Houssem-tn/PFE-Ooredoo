package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UsedEquipmentRequestItem(
        @NotBlank String equipmentId,
        String equipmentName,
        String unit,
        @Positive int quantity,
        /**
         * Exact tracked-unit ids consumed for this line. Required (size == quantity) when the
         * equipment's trackingMode != NONE; null/empty for quantity-based items.
         */
        List<String> instanceIds
) {
}
