package com.pfe.stock_management.material_return;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMaterialReturnDto {

    // Optional case link
    private String relatedCaseId;
    private String relatedCaseType;
    private String crmCase;

    @NotNull(message = "Return source is required")
    private ReturnSource returnSource;

    // ISO date "YYYY-MM-DD" — optional; declared when STT plans to hand over the material
    private String plannedReturnDate;

    @NotNull(message = "Return reason is required")
    private ReturnReason reason;

    @NotNull(message = "Return condition is required")
    private ReturnCondition condition;

    @NotNull(message = "Return destination is required")
    private ReturnDestination destination;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<ReturnedItemDto> items;

    private String comment;

    private List<String> proofImageUrls;
}
