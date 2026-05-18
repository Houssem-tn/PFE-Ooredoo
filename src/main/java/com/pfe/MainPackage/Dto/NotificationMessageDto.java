package com.pfe.MainPackage.Dto;

import java.time.Instant;
import java.util.Map;

public class NotificationMessageDto {

    private String id;

    private String eventType;
    private String title;
    private String message;

    private String entityType;
    private String entityId;

    private String status;

    private String targetRole;
    private String recipientEmail;

    private String triggeredBy;

    private boolean read;
    private Instant createdAt;

    private Map<String, Object> data;

    // ── Alert Center fields ───────────────────────────────────────────────────
    private String level;       // CRITICAL | WARNING | INFO
    private String actionType;  // OPEN_CASE | OPEN_DPM_REQUEST | ...
    private String relatedCaseId;
    private String relatedStockRequestId;
    private String relatedPermissionRequestId;

    public NotificationMessageDto() {
    }

    public NotificationMessageDto(String id,
                                  String eventType,
                                  String title,
                                  String message,
                                  String entityType,
                                  String entityId,
                                  String status,
                                  String targetRole,
                                  String recipientEmail,
                                  String triggeredBy,
                                  boolean read,
                                  Instant createdAt,
                                  Map<String, Object> data) {
        this.id = id;
        this.eventType = eventType;
        this.title = title;
        this.message = message;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = status;
        this.targetRole = targetRole;
        this.recipientEmail = recipientEmail;
        this.triggeredBy = triggeredBy;
        this.read = read;
        this.createdAt = createdAt;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getRelatedCaseId() { return relatedCaseId; }
    public void setRelatedCaseId(String relatedCaseId) { this.relatedCaseId = relatedCaseId; }

    public String getRelatedStockRequestId() { return relatedStockRequestId; }
    public void setRelatedStockRequestId(String relatedStockRequestId) { this.relatedStockRequestId = relatedStockRequestId; }

    public String getRelatedPermissionRequestId() { return relatedPermissionRequestId; }
    public void setRelatedPermissionRequestId(String relatedPermissionRequestId) { this.relatedPermissionRequestId = relatedPermissionRequestId; }
}