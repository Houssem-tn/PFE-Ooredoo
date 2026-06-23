package com.pfe.stock_management.material_return;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnedItemDto {

    @NotBlank(message = "Equipment ID is required")
    private String equipmentId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    private String serialNumber;
    private String macAddress;
    private String imei;
    private ReturnCondition condition;
}
