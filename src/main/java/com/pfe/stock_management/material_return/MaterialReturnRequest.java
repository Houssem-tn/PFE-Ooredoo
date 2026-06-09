package com.pfe.stock_management.material_return;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_material_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReturnRequest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String returnCode; // e.g. RET-2026-0001

    // ── STT identity ──────────────────────────────────────────────────────────
    private String sttId;
    private String sttName;
    private String sttEmail;

    // ── Responsible ZM (selected by STT at creation) ─────────────────────────
    private String zmId;
    private String zmName;
    private String zmEmail;

    // ── Optional case link ────────────────────────────────────────────────────
    private String relatedCaseId;
    private String relatedCaseType; // ACT, RES, PLT, DMG
    private String crmCase;

    // ── Source DPM (audit trail link) ─────────────────────────────────────────
    private String sourceDpmId;

    // ── Return classification ─────────────────────────────────────────────────
    private ReturnSource returnSource;
    private ReturnReason reason;
    private ReturnCondition condition;
    private ReturnDestination destination;

    @Builder.Default
    private List<ReturnedItem> items = new ArrayList<>();

    private String comment;

    @Builder.Default
    private List<String> proofImageUrls = new ArrayList<>();

    // ── Status ────────────────────────────────────────────────────────────────
    private MaterialReturnStatus status;

    // ── ZM: proposed handover schedule ───────────────────────────────────────
    private String proposedHandoverDate;   // ISO "YYYY-MM-DD"
    private String proposedHandoverTime;   // "HH:mm"
    private String proposedHandoverNote;   // optional ZM instructions
    private Instant scheduleProposedAt;

    // ── STT: schedule response ────────────────────────────────────────────────
    private String sttScheduleResponseComment; // comment when STT rejects schedule
    private Instant scheduleConfirmedAt;
    private Instant scheduleRejectedAt;

    // ── ZM: rejection of return itself ───────────────────────────────────────
    private String reviewComment;          // ZM rejection reason
    private String reviewedByZmId;
    private String reviewedByZmName;
    private Instant reviewedAt;

    // ── Physical handover completion (ZM marks receipt) ───────────────────────
    private Instant handedOverAt;
    private String handedOverNote;

    // ── Timestamps ────────────────────────────────────────────────────────────
    private Instant createdAt;
    private Instant submittedAt;

    // ── Audit timeline ────────────────────────────────────────────────────────
    @Builder.Default
    private List<ReturnTimelineEvent> timeline = new ArrayList<>();
}
