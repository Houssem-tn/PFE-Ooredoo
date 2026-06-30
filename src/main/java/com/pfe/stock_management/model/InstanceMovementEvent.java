package com.pfe.stock_management.model;

import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.InstanceStatus;
import lombok.*;

import java.time.Instant;

/**
 * One step in a tracked {@link com.pfe.stock_management.entity.EquipmentInstance}'s movement
 * history. Append-only audit record embedded on the instance document.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceMovementEvent {

    /** RECEIVED_FROM_DPM | DISTRIBUTED_TO_TECH | USED_ON_CASE | USAGE_RESTORED | RECOVERED_FROM_CLIENT | RETURNED_TO_DEPOT */
    private String type;

    private InstanceOwnerType fromOwnerType;
    private String fromOwnerId;
    private String fromOwnerName;

    private InstanceOwnerType toOwnerType;
    private String toOwnerId;
    private String toOwnerName;

    /** Resulting status after this movement (snapshot for audit). */
    private InstanceStatus resultingStatus;

    // ── Cross-references (null when not applicable) ──────────────────────────
    private String caseId;
    private String caseReference;
    private String dpmId;
    private String dpmNumber;
    private String distributionId;
    private String returnId;
    private String returnCode;

    private String note;
    private Instant occurredAt;
    private String performedBy;
}
