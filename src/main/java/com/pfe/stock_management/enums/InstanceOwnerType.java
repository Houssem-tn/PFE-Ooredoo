package com.pfe.stock_management.enums;

/**
 * Who currently physically holds a tracked {@link com.pfe.stock_management.entity.EquipmentInstance}.
 * Mirrors the lifecycle Depot/DPM → STT → TECH → client → return/depot.
 */
public enum InstanceOwnerType {
    /** Held in an STT warehouse. */
    STT,
    /** Held by a field technician. */
    TECH,
    /** Installed at / held by the end client (after case usage). */
    CLIENT,
    /** Back in the central depot / team stock. */
    DEPOT,
    /** In transit through the material-return workflow. */
    RETURN,
    /** Held by a Zone Manager after collecting retrieved stock from a technician. */
    ZM
}
