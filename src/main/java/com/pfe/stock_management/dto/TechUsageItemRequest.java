package com.pfe.stock_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

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

    /**
     * Exact tracked-unit ids consumed for this line. Required (size == quantity) when the
     * equipment's trackingMode != NONE; null/empty for quantity-based items.
     */
    private List<String> instanceIds;
}
