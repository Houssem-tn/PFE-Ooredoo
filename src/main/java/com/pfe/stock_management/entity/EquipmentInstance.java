package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.InstanceStatus;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.model.InstanceMovementEvent;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single physical unit of a tracked {@link Equipment} (trackingMode != NONE), followed
 * individually by IMEI and/or Serial Number across its full lifecycle:
 * Depot/DPM → STT → TECH → case/client → recovery/return → depot.
 *
 * <p>This sits <b>alongside</b> the quantity-based counters (SttStockItem / TechStockItem):
 * the counters remain authoritative for totals; these instances add per-unit traceability
 * for high-value devices. Non-tracked equipment never produces instances.
 *
 * <p>{@code normalizedImei} / {@code normalizedSerialNumber} hold the uppercased+trimmed
 * identifiers and back the per-equipment sparse unique indexes
 * (see {@code EquipmentInstanceIndexConfig}). Uniqueness is scoped to {@code equipmentId}.
 */
@Document(collection = "stock_equipment_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentInstance {

    @Id
    private String id;

    private String equipmentId;
    private String equipmentName;
    private String articleCode;

    /** Snapshot of the equipment's trackingMode at creation time. */
    private TrackingMode trackingMode;

    // ── Identifiers (as entered) ─────────────────────────────────────────────
    private String imei;
    private String serialNumber;

    // ── Normalised identifiers (uppercase+trim) for the unique indexes ───────
    private String normalizedImei;
    private String normalizedSerialNumber;

    // ── Provenance ───────────────────────────────────────────────────────────
    private String sourceDpmId;
    private String sourceDpmNumber;

    /**
     * The STT that first received this unit (from the DPM, or the case STT for a RES recovery).
     * Stable across the unit's lifecycle and used to scope the ZM "Unit Tracking" audit to a
     * ZM's territory even after the unit moves to a TECH / client / depot.
     */
    private String originSttId;

    // ── Current location ──────────────────────────────────────────────────────
    private InstanceOwnerType currentOwnerType;
    private String currentOwnerId;
    private String currentOwnerName;

    @Builder.Default
    private InstanceStatus status = InstanceStatus.IN_STT_STOCK;

    // ── Case / return links (set as the unit moves) ──────────────────────────
    private String caseId;
    private String caseReference;
    private String returnId;
    private String returnCode;

    /**
     * Linked {@code RetrievedStockItem} id when this unit was retrieved from a client through the
     * technician Retrieved-Stock workflow (PLT/RES), as opposed to the older RES-only
     * {@code MaterialReturnRequest} recovery path (which uses {@link #returnId}/{@link #returnCode}
     * instead). Null for units that never went through that workflow.
     */
    private String retrievedStockItemId;

    // ── Movement history (append-only) ───────────────────────────────────────
    @Builder.Default
    private List<InstanceMovementEvent> movements = new ArrayList<>();

    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
