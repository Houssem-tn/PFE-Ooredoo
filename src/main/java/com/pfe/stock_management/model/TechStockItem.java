package com.pfe.stock_management.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechStockItem {

    private String equipmentId;
    private String equipmentName;
    private String unit;

    @Builder.Default
    private int receivedQuantity = 0;

    @Builder.Default
    private int usedQuantity = 0;

    private Instant lastReceivedAt;

    public int getRemainingQuantity() {
        return receivedQuantity - usedQuantity;
    }
}
