package com.pfe.stock_management.dto;

import com.pfe.stock_management.enums.EquipmentCategory;
import com.pfe.stock_management.enums.StockUnit;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEquipmentRequest {

    @NotBlank
    private String name;

    private String articleCode;

    private String description;
    private StockUnit unit;
    private EquipmentCategory category;
}
