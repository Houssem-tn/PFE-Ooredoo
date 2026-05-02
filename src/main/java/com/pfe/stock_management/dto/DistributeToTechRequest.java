package com.pfe.stock_management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DistributeToTechRequest {

    @NotBlank
    private String techId;

    private String dpmId;

    @NotEmpty
    @Valid
    private List<DistributionItemRequest> items;

    private String notes;
}
