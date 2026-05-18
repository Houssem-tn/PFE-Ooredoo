package com.pfe.stock_management.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionItem {
    private String itemId;
    private String itemName;
    private double requestedQuantity;
    private double approvedQuantity;
    private String unit;
}
