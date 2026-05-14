package com.pfe.stock_management.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageItem {

    private String equipmentId;
    private String equipmentName;
    private String unit;

    @Builder.Default
    private int quantity = 0;
}
