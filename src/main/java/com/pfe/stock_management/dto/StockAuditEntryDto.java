package com.pfe.stock_management.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified, read-only audit entry that represents a single stock lifecycle record
 * — either a DPM distribution request or a material return request — mapped to a
 * common shape so the Admin Stock Audits feed can list both side by side.
 *
 * <p>This DTO is purely an audit projection: it never feeds back into the DPM or
 * material-return workflows and contains no behaviour.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAuditEntryDto {

    /** DPM | RETURN_MATERIAL */
    private String auditType;

    /** MongoDB id of the underlying DpmRequest / MaterialReturnRequest. */
    private String id;

    /** Human reference — DPM number or return code (e.g. DPM-2026-0007 / RET-2026-0003). */
    private String reference;

    // ── Status ──────────────────────────────────────────────────────────────
    /** Raw status name of the underlying entity. */
    private String status;
    /** Human-friendly status label. */
    private String statusLabel;
    /** Normalised badge category: PENDING | IN_PROGRESS | CONFIRMED | COMPLETED | REJECTED | CANCELLED */
    private String statusCategory;

    // ── Primary actor (who last acted on the record) ────────────────────────
    private String actorName;
    private String actorRole;

    // ── Parties involved ────────────────────────────────────────────────────
    private String sttId;
    private String sttName;
    private String zmId;
    private String zmName;
    private String technicianName;

    // ── Items / quantities ──────────────────────────────────────────────────
    @Builder.Default
    private List<Item> items = new ArrayList<>();
    private int totalQuantity;

    // ── Return-specific classification (null for DPM) ───────────────────────
    private String reason;
    private String returnSource;
    private String returnCondition;
    private String destination;

    // ── Case link ───────────────────────────────────────────────────────────
    private String relatedCaseId;
    private String crmCase;
    private String relatedCaseType;

    // ── Free text ───────────────────────────────────────────────────────────
    private String notes;

    // ── Dates ───────────────────────────────────────────────────────────────
    private Instant createdAt;
    /** Last-activity timestamp used for newest-first ordering. */
    private Instant updatedAt;

    // ── Lifecycle timeline (for the detail panel) ───────────────────────────
    @Builder.Default
    private List<Event> timeline = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String equipmentId;
        private String equipmentName;
        private String unit;
        private int quantity;
        /** Returned-item condition (GOOD / DAMAGED / ...). Null for DPM items. */
        private String condition;
        private String serialNumber;
        private String imei;
        private String retrievedStockItemId;
        private String technicianName;
        private String originalCaseReference;
        private String originalCaseType;
        private String originalCustomerName;
        private Instant originalRecoveryDate;
        private Instant sttCollectedAt;
        private String currentReturnStatus;
        private String proofViewUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Event {
        private String eventType;
        private String description;
        private String actorName;
        private String actorRole;
        private Instant timestamp;
        private String note;
    }
}
