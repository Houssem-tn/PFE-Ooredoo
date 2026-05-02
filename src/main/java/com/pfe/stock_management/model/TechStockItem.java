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

    @Builder.Default
    private int receivedQuantity = 0;

    private Instant lastReceivedAt;
}
