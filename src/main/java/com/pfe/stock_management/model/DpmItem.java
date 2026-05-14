package com.pfe.stock_management.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmItem {

    private String equipmentId;
    private String equipmentName;
    private String equipmentImageUrl;
    private String unit;

    private int requestedQuantity;
    private Integer confirmedQuantity;
    private String confirmationNote;

    public int getShortageQuantity() {
        if (confirmedQuantity == null) return 0;
        return Math.max(0, requestedQuantity - confirmedQuantity);
    }
}
