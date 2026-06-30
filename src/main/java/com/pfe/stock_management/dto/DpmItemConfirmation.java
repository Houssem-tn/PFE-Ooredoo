package com.pfe.stock_management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DpmItemConfirmation {

    @NotBlank
    private String equipmentId;

    @Min(0)
    private int confirmedQuantity;

    private String confirmationNote;

    /**
     * Per-physical-unit detail rows for tracked equipment (IMEI / Serial). Must contain exactly
     * {@code confirmedQuantity} rows when the equipment's trackingMode != NONE. Null / empty for
     * quantity-based items. Validated server-side by {@code EquipmentInstanceService}.
     */
    @Valid
    private List<UnitTrackingInput> units;
}
