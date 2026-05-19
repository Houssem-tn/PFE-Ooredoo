package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.StockThreshold;

import java.time.Instant;

public class StockThresholdResponse {

    private String id;
    private String zmId;
    private String zmName;
    private String itemId;
    private String itemName;
    private String itemType;
    private double warningThreshold;
    private double criticalThreshold;
    private String unit;
    private boolean enabled;
    private Double caseUsageLimit;
    private boolean caseUsageLimitEnabled;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;

    public static StockThresholdResponse from(StockThreshold t) {
        StockThresholdResponse r = new StockThresholdResponse();
        r.id = t.getId();
        r.zmId = t.getZmId();
        r.zmName = t.getZmName();
        r.itemId = t.getItemId();
        r.itemName = t.getItemName();
        r.itemType = t.getItemType();
        r.warningThreshold = t.getWarningThreshold();
        r.criticalThreshold = t.getCriticalThreshold();
        r.unit = t.getUnit();
        r.enabled = t.isEnabled();
        r.caseUsageLimit = t.getCaseUsageLimit();
        r.caseUsageLimitEnabled = t.isCaseUsageLimitEnabled();
        r.createdAt = t.getCreatedAt();
        r.updatedAt = t.getUpdatedAt();
        r.updatedBy = t.getUpdatedBy();
        return r;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getZmId() { return zmId; }
    public String getZmName() { return zmName; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getItemType() { return itemType; }
    public double getWarningThreshold() { return warningThreshold; }
    public double getCriticalThreshold() { return criticalThreshold; }
    public String getUnit() { return unit; }
    public boolean isEnabled() { return enabled; }
    public Double getCaseUsageLimit() { return caseUsageLimit; }
    public boolean isCaseUsageLimitEnabled() { return caseUsageLimitEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
