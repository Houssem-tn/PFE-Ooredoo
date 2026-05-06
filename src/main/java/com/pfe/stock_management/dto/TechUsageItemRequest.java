package com.pfe.stock_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechUsageItemRequest {

    @NotBlank
    private String equipmentId;

    private String equipmentName;
    private String unit;

    @Positive
    private int quantity;
}
