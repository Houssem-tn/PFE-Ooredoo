package com.pfe.stock_management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class StockThresholdRequest {

    @NotBlank
    private String itemId;

    @NotBlank
    private String itemName;

    /** EQUIPMENT or CABLE */
    private String itemType;

    /** PIECE or METER */
    private String unit;

    // ── STT-specific thresholds ────────────────────────────────────────────────
    @Min(0)
    private Double sttWarningThreshold;

    @Min(0)
    private Double sttCriticalThreshold;

    private Boolean sttAlertsEnabled;

    // ── TECH-specific thresholds ───────────────────────────────────────────────
    @Min(0)
    private Double techWarningThreshold;

    @Min(0)
    private Double techCriticalThreshold;

    private Boolean techAlertsEnabled;

    // ── Legacy fields (kept for backwards compat — old clients still send these) ──
    @Min(0)
    private Double warningThreshold;

    @Min(0)
    private Double criticalThreshold;

    private boolean enabled = true;

    // ── Case usage limit ───────────────────────────────────────────────────────
    @Min(0)
    private Double caseUsageLimit;

    private boolean caseUsageLimitEnabled = false;

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getSttWarningThreshold() { return sttWarningThreshold; }
    public void setSttWarningThreshold(Double v) { this.sttWarningThreshold = v; }

    public Double getSttCriticalThreshold() { return sttCriticalThreshold; }
    public void setSttCriticalThreshold(Double v) { this.sttCriticalThreshold = v; }

    public Boolean getSttAlertsEnabled() { return sttAlertsEnabled; }
    public void setSttAlertsEnabled(Boolean v) { this.sttAlertsEnabled = v; }

    public Double getTechWarningThreshold() { return techWarningThreshold; }
    public void setTechWarningThreshold(Double v) { this.techWarningThreshold = v; }

    public Double getTechCriticalThreshold() { return techCriticalThreshold; }
    public void setTechCriticalThreshold(Double v) { this.techCriticalThreshold = v; }

    public Boolean getTechAlertsEnabled() { return techAlertsEnabled; }
    public void setTechAlertsEnabled(Boolean v) { this.techAlertsEnabled = v; }

    public Double getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(Double warningThreshold) { this.warningThreshold = warningThreshold; }

    public Double getCriticalThreshold() { return criticalThreshold; }
    public void setCriticalThreshold(Double criticalThreshold) { this.criticalThreshold = criticalThreshold; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Double getCaseUsageLimit() { return caseUsageLimit; }
    public void setCaseUsageLimit(Double caseUsageLimit) { this.caseUsageLimit = caseUsageLimit; }

    public boolean isCaseUsageLimitEnabled() { return caseUsageLimitEnabled; }
    public void setCaseUsageLimitEnabled(boolean caseUsageLimitEnabled) { this.caseUsageLimitEnabled = caseUsageLimitEnabled; }
}
