package com.pfe.stock_management.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumedEquipmentItem {

    private String equipmentId;
    private String equipmentName;
    private String unit;
    private int quantityUsed;
    private int stockBefore;
    private int stockAfter;
}
