package com.pfe.stock_management.entity;

import com.pfe.stock_management.model.PermissionItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * TECH requests permission from STT to consume more than the automatic limit:
 *  - cable > 20 metres  OR
 *  - any equipment item > 1 piece
 *
 * Only STT can approve/reject. ZM reads only (no action).
 * Backend enforces the limit; Angular cannot bypass it.
 */
@Document(collection = "stock_usage_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockUsagePermissionRequest {

    @Id
    private String id;

    @Indexed
    private String caseId;

    private String caseReference;

    @Indexed
    private String techId;
    private String techName;
    private String techEmail;

    @Indexed
    private String sttId;
    private String sttName;
    private String sttEmail;

    private String zmId;
    private String zmName;

    /**
     * PENDING | APPROVED | PARTIALLY_APPROVED | REJECTED | CANCELLED
     */
    private String status;

    private String reason;

    // ── Cable ─────────────────────────────────────────────────────────────────

    private double requestedCableMeters;
    private double approvedCableMeters;

    // ── Equipment items ───────────────────────────────────────────────────────

    private List<PermissionItem> requestedItems;
    private List<PermissionItem> approvedItems;

    // ── Audit ─────────────────────────────────────────────────────────────────

    private Instant createdAt;
    private Instant reviewedAt;
    private String reviewedBySttId;
    private String reviewComment;
}
