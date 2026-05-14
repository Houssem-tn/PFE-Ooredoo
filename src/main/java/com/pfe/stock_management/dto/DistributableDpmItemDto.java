package com.pfe.stock_management.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributableDpmItemDto {
    private String equipmentId;
    private String equipmentName;
    private String unit;
    private int confirmedQuantity;
    private int distributedQuantity;
    private int remainingQuantity;
}
