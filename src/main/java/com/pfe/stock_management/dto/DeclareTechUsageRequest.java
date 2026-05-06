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
@Builder
public class DeclareTechUsageRequest {

    @NotBlank
    private String caseId;

    @NotEmpty
    @Valid
    private List<TechUsageItemRequest> items;

    private String notes;
}
