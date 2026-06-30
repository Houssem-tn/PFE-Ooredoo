package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.EquipmentCategory;
import com.pfe.stock_management.enums.StockUnit;
import com.pfe.stock_management.enums.TrackingMode;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stock_equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    private String id;

    private String name;
    private String articleCode;
    private String description;
    private StockUnit unit;
    private EquipmentCategory category;

    private String imageFileId;

    @Transient
    private String imageUrl;

    // ── Business usage classification ────────────────────────────────────────
    // Drives which field workflow may consume / recover this equipment.
    // Legacy documents created before this feature have these fields absent;
    // EquipmentClassificationMigration backfills them with permissive defaults
    // (usableInAct=true, usableInPlt=true, packItem=false, recoverableFromClient=false)
    // so existing ACT/PLT consumption keeps working until an admin refines them.

    /** Boutique pack item (e.g. ODU P90, IDU). Pack items are blocked from ACT consumption. */
    @Builder.Default
    private boolean packItem = false;

    /** Consumable during an ACT activation (cable, connecteur, fixation, ...). */
    @Builder.Default
    private boolean usableInAct = true;

    /** Consumable / swappable during a PLT complaint (ODU, IDU, modem replacing broken client material). */
    @Builder.Default
    private boolean usableInPlt = true;

    /** Can be retrieved from the client during a RES résiliation and declared as recovered material. */
    @Builder.Default
    private boolean recoverableFromClient = false;

    // ── Per-unit tracking configuration ──────────────────────────────────────
    // Drives whether physical units are tracked individually by IMEI / Serial Number.
    // Legacy documents created before this feature have this field absent; Mongo would
    // deserialise a missing enum to null, so EquipmentTrackingModeMigration backfills
    // legacy rows with NONE (quantity-based, unchanged behaviour). Tracked items
    // additionally materialise EquipmentInstance rows across their lifecycle.
    @Builder.Default
    private TrackingMode trackingMode = TrackingMode.NONE;

    @Builder.Default
    private boolean active = true;

    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
