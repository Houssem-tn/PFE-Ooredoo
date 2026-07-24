package com.pfe.stock_management.enums;

/**
 * Lifecycle status of a tracked {@link com.pfe.stock_management.entity.EquipmentInstance}.
 */
public enum InstanceStatus {
    /** Received from a DPM and sitting in the STT's stock. */
    IN_STT_STOCK,
    /** Distributed to a TECH and sitting in the TECH's stock. */
    IN_TECH_STOCK,
    /** Consumed/installed on a case (ACT activation or PLT swap). */
    USED_ON_CASE,
    /** Retrieved from the client during a RES résiliation, pending handover. */
    RECOVERED_FROM_CLIENT,
    /** Handed back to the ZM/depot through the material-return workflow. */
    RETURNED_TO_DEPOT,
    /**
     * Retrieved from a client during a PLT/RES intervention via the technician Retrieved-Stock
     * workflow; still physically held by the TECH, pending STT collection.
     * (Historically named for ZM collection; kept as the pending value for backward compatibility.)
     */
    PENDING_ZM_COLLECTION,
    /** @deprecated Physically collected by a ZM (legacy flow). Kept for historical documents. */
    @Deprecated
    COLLECTED_BY_ZM
}
