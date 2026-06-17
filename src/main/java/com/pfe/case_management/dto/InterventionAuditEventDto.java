package com.pfe.case_management.dto;

import lombok.*;

import java.time.Instant;

/**
 * One flattened intervention/case audit event, projected from the embedded
 * {@code CaseEntity.timeline}. Read-only; never written back into a case.
 *
 * <p>Stock-related timeline events (DPM, stock movement, material returns) are
 * intentionally excluded here — those belong to the separate Stock Audits module.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterventionAuditEventDto {

    /** Timeline event id. */
    private String id;

    // ── Case context ────────────────────────────────────────────────────────
    private String caseId;
    private String caseReference;   // crmCase
    private String module;          // ACT | PLT | RES
    private String caseType;        // ACTIVATION | PLAINTE | RESILIATION

    // ── Event ───────────────────────────────────────────────────────────────
    private String eventType;       // raw CaseTimelineEventType name
    private String category;        // ASSIGNMENT | FIELD_EXECUTION | SLA_ESCALATION | COMPLETION_CLOSURE | LIFECYCLE
    private String title;
    private String description;

    // ── Actor (who triggered it) ────────────────────────────────────────────
    private String actorName;
    private String actorRole;       // ZM | STT | TECH | ADMIN | SYSTEM | null
    private String actorEmail;

    // ── Target user (assignment events) ─────────────────────────────────────
    private String targetName;
    private String targetRole;      // ZM | STT | TECH
    private String targetEmail;

    // ── Status transition ───────────────────────────────────────────────────
    private String previousStatus;
    private String newStatus;

    // ── When ────────────────────────────────────────────────────────────────
    private Instant occurredAt;

    // ── Where (zone) ────────────────────────────────────────────────────────
    private String governorate;
    private String delegation;
    private String localite;

    // ── GPS proof (if captured with the event) ──────────────────────────────
    private String latitude;
    private String longitude;
    private Instant gpsCapturedAt;
    private Double gpsAccuracyMeters;

    // ── Photo proof (if attached with the event) ────────────────────────────
    private String proofFileId;
    private String proofFilename;

    // ── SLA / escalation (if applicable) ────────────────────────────────────
    private String slaStatus;
    private Long breachAgeMinutes;
}
