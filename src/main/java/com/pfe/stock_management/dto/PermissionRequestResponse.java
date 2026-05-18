package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.StockUsagePermissionRequest;
import com.pfe.stock_management.model.PermissionItem;

import java.time.Instant;
import java.util.List;

public class PermissionRequestResponse {

    private String id;
    private String caseId;
    private String caseReference;
    private String techId;
    private String techName;
    private String sttId;
    private String sttName;
    private String zmId;
    private String zmName;
    private String status;
    private String reason;
    private double requestedCableMeters;
    private double approvedCableMeters;
    private List<PermissionItem> requestedItems;
    private List<PermissionItem> approvedItems;
    private Instant createdAt;
    private Instant reviewedAt;
    private String reviewedBySttId;
    private String reviewComment;

    public static PermissionRequestResponse from(StockUsagePermissionRequest r) {
        PermissionRequestResponse resp = new PermissionRequestResponse();
        resp.id = r.getId();
        resp.caseId = r.getCaseId();
        resp.caseReference = r.getCaseReference();
        resp.techId = r.getTechId();
        resp.techName = r.getTechName();
        resp.sttId = r.getSttId();
        resp.sttName = r.getSttName();
        resp.zmId = r.getZmId();
        resp.zmName = r.getZmName();
        resp.status = r.getStatus();
        resp.reason = r.getReason();
        resp.requestedCableMeters = r.getRequestedCableMeters();
        resp.approvedCableMeters = r.getApprovedCableMeters();
        resp.requestedItems = r.getRequestedItems();
        resp.approvedItems = r.getApprovedItems();
        resp.createdAt = r.getCreatedAt();
        resp.reviewedAt = r.getReviewedAt();
        resp.reviewedBySttId = r.getReviewedBySttId();
        resp.reviewComment = r.getReviewComment();
        return resp;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getCaseReference() { return caseReference; }
    public String getTechId() { return techId; }
    public String getTechName() { return techName; }
    public String getSttId() { return sttId; }
    public String getSttName() { return sttName; }
    public String getZmId() { return zmId; }
    public String getZmName() { return zmName; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public double getRequestedCableMeters() { return requestedCableMeters; }
    public double getApprovedCableMeters() { return approvedCableMeters; }
    public List<PermissionItem> getRequestedItems() { return requestedItems; }
    public List<PermissionItem> getApprovedItems() { return approvedItems; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBySttId() { return reviewedBySttId; }
    public String getReviewComment() { return reviewComment; }
}
