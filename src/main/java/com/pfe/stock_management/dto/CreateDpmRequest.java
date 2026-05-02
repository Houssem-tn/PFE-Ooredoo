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
public class CreateDpmRequest {

    @NotBlank
    private String sttId;

    @NotBlank
    private String deliveryPersonName;

    @NotEmpty
    @Valid
    private List<DpmItemRequest> items;

    private String notes;
}
