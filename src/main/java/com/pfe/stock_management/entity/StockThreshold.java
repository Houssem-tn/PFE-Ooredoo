package com.pfe.stock_management.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * ZM-configurable per-item stock threshold.
 * WARNING alert fires when quantity <= warningThreshold.
 * CRITICAL alert fires when quantity <= criticalThreshold (usually 0).
 */
@Document(collection = "stock_thresholds")
@CompoundIndex(def = "{'zmId': 1, 'itemId': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockThreshold {

    @Id
    private String id;

    @Indexed
    private String zmId;
    private String zmName;

    private String itemId;
    private String itemName;

    /** EQUIPMENT or CABLE */
    private String itemType;

    /** Quantity at which a WARNING alert is created. */
    private double warningThreshold;

    /** Quantity at which a CRITICAL alert is created (usually 0). */
    private double criticalThreshold;

    /** PIECE or METER */
    private String unit;

    @Builder.Default
    private boolean enabled = true;

    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
}
