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

    // ── Legacy combined thresholds (kept for backwards compat with old documents) ──
    /** @deprecated Use sttWarningThreshold / techWarningThreshold instead. */
    private double warningThreshold;
    /** @deprecated Use sttCriticalThreshold / techCriticalThreshold instead. */
    private double criticalThreshold;
    /** Master enable flag — true when either sttAlertsEnabled or techAlertsEnabled is true. */
    @Builder.Default
    private boolean enabled = true;

    // ── STT-specific thresholds (null = not yet configured via new UI → falls back to legacy) ──
    /** WARNING alert fires to ZM when STT quantity ≤ this value. */
    private Double sttWarningThreshold;
    /** CRITICAL alert fires to ZM when STT quantity ≤ this value. */
    private Double sttCriticalThreshold;
    /** Whether STT stock alerts are active for this item. */
    private Boolean sttAlertsEnabled;

    // ── TECH-specific thresholds ────────────────────────────────────────────────
    /** WARNING alert fires to STT when TECH quantity ≤ this value. */
    private Double techWarningThreshold;
    /** CRITICAL alert fires to STT + ZM when TECH quantity ≤ this value. */
    private Double techCriticalThreshold;
    /** Whether TECH stock alerts are active for this item. */
    private Boolean techAlertsEnabled;

    /** PIECE or METER */
    private String unit;

    /**
     * Maximum quantity/meters a TECH may consume in one case without STT approval.
     * Null means the ZM has not configured a limit for this item.
     */
    private Double caseUsageLimit;

    /**
     * Whether the per-case usage limit is active for this item.
     * When false the TECH can use any quantity without permission (stock availability still enforced).
     */
    @Builder.Default
    private boolean caseUsageLimitEnabled = false;

    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
}
