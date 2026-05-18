package com.pfe.stock_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public class CreatePermissionRequest {

    @NotBlank
    private String caseId;

    private String reason;

    @PositiveOrZero
    private double requestedCableMeters;

    private List<PermissionItemRequest> requestedItems;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getRequestedCableMeters() { return requestedCableMeters; }
    public void setRequestedCableMeters(double requestedCableMeters) { this.requestedCableMeters = requestedCableMeters; }

    public List<PermissionItemRequest> getRequestedItems() { return requestedItems; }
    public void setRequestedItems(List<PermissionItemRequest> requestedItems) { this.requestedItems = requestedItems; }

    public static class PermissionItemRequest {
        private String itemId;
        private String itemName;
        private double requestedQuantity;
        private String unit;

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public double getRequestedQuantity() { return requestedQuantity; }
        public void setRequestedQuantity(double requestedQuantity) { this.requestedQuantity = requestedQuantity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }
}
