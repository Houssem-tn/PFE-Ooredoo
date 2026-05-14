package com.pfe.stock_management.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttStockItem {

    private String equipmentId;
    private String equipmentName;
    private String unit;

    @Builder.Default
    private int totalReceived = 0;

    @Builder.Default
    private int totalDistributedToTechs = 0;

    private Instant lastReceivedAt;
    private Instant lastDistributedAt;

    public int getAvailableQuantity() {
        return Math.max(0, totalReceived - totalDistributedToTechs);
    }
}
