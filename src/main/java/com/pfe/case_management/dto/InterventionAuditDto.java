package com.pfe.case_management.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Case-centric operational audit record for one field intervention. Aggregates the
 * full assignment chain, key lifecycle timings, SLA/escalation, proof/GPS metadata,
 * consumed materials and the event timeline into a single read-only projection for the
 * Admin "Interventions Auditing" cockpit.
 *
 * <p>Read-only — it never mutates a case or its workflow. Consumed materials are shown
 * for intervention traceability; the stock lifecycle itself lives in Stock Audits.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterventionAuditDto {

    /** Audit identity for this intervention (= caseId). */
    private String auditId;
    private String caseId;
    private String caseReference;   // crmCase
    private String module;          // ACT | PLT | RES
    private String caseType;        // ACTIVATION | PLAINTE | RESILIATION

    private String currentStatus;

    // ── Last action summary (most recent timeline event) ────────────────────
    private String eventType;
    private String statusBefore;
    private String statusAfter;
    private String actorId;
    private String actorName;
    private String actorRole;
    private String lastActionTitle;
    private Instant lastActionAt;

    // ── Assignment chain ────────────────────────────────────────────────────
    private String zmId;
    private String zmName;
    private String sttId;
    private String sttName;
    private String technicianId;
    private String technicianName;

    // ── Location / customer ─────────────────────────────────────────────────
    private String governorate;
    private String delegation;
    private String localite;
    private String customerName;
    private String customerPhone;

    // ── Key lifecycle timings ───────────────────────────────────────────────
    private Instant createdAt;
    private Instant assignedAt;       // executor assignment (tech, else stt)
    private Instant assignedZmAt;
    private Instant assignedSttAt;
    private Instant assignedTechAt;
    private Instant rdvAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant validatedAt;
    private Instant closedAt;

    // ── SLA / escalation ────────────────────────────────────────────────────
    private String slaStatus;         // raw SlaStatus name
    private String slaCategory;       // ON_TIME | WARNING | BREACHED | NONE
    private Instant slaDeadline;
    private int escalationLevel;

    // ── Proof / GPS ─────────────────────────────────────────────────────────
    private boolean proofPresent;
    private int proofCount;
    private String proofFileId;
    private String proofFileName;
    private Double gpsLatitude;
    private Double gpsLongitude;
    private Double gpsAccuracy;

    // ── Materials consumed ──────────────────────────────────────────────────
    @Builder.Default
    private List<ConsumedMaterialDto> consumedMaterials = new ArrayList<>();
    private int materialCount;
    private int totalMaterialQuantity;

    // ── Related material returns (cross-link only) ──────────────────────────
    @Builder.Default
    private List<InterventionMaterialReturnRefDto> relatedReturns = new ArrayList<>();

    // ── Full timeline (drawer) ──────────────────────────────────────────────
    @Builder.Default
    private List<InterventionAuditEventDto> timeline = new ArrayList<>();

    private String notes;
}
