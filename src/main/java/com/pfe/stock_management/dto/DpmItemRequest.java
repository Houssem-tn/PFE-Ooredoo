package com.pfe.stock_management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DpmItemRequest {

    @NotBlank
    private String equipmentId;

    @Min(1)
    private int requestedQuantity;
}
