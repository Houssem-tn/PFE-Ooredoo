package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UsedEquipmentRequestItem(
        @NotBlank String equipmentId,
        String equipmentName,
        String unit,
        @Positive int quantity
) {
}
