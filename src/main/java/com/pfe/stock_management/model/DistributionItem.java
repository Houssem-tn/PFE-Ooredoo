package com.pfe.stock_management.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributionItem {

    private String equipmentId;
    private String equipmentName;
    private int quantity;
}
