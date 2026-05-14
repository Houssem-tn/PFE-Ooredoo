package com.pfe.stock_management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

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
}
