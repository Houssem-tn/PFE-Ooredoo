package com.pfe.stock_management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DistributionItemRequest {

    @NotBlank
    private String equipmentId;

    @Min(1)
    private int quantity;

    /**
     * Exact tracked-unit ids to distribute for this line. Required (size == quantity) when the
     * equipment's trackingMode != NONE; ignored for quantity-based items.
     */
    private List<String> instanceIds;
}
