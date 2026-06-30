package com.pfe.stock_management.enums;

/**
 * Per-unit tracking configuration for an {@link com.pfe.stock_management.entity.Equipment}.
 *
 * <p>Fungible items (cable, connecteur, fixation) are {@link #NONE} and stay purely
 * quantity-based. High-value devices (ODU P90, IDU, modem) require individual physical-unit
 * tracking by IMEI and/or Serial Number across their whole lifecycle (DPM → STT → TECH →
 * case/client → return/recovery/depot) via {@link com.pfe.stock_management.entity.EquipmentInstance}.
 */
public enum TrackingMode {

    /** No per-unit tracking — quantity-based stock only (default, legacy behaviour). */
    NONE,

    /** Each physical unit must declare a Serial Number. */
    SERIAL_ONLY,

    /** Each physical unit must declare an IMEI. */
    IMEI_ONLY,

    /** Each physical unit must declare BOTH a Serial Number and an IMEI. */
    SERIAL_AND_IMEI,

    /** Each physical unit must declare AT LEAST ONE of Serial Number or IMEI. */
    SERIAL_OR_IMEI;

    /** True when this mode requires individual {@code EquipmentInstance} records per unit. */
    public boolean isTracked() {
        return this != NONE;
    }

    /** Whether a Serial Number is mandatory for every unit under this mode. */
    public boolean requiresSerial() {
        return this == SERIAL_ONLY || this == SERIAL_AND_IMEI;
    }

    /** Whether an IMEI is mandatory for every unit under this mode. */
    public boolean requiresImei() {
        return this == IMEI_ONLY || this == SERIAL_AND_IMEI;
    }

    /** Whether at least one of Serial / IMEI is mandatory (but not a specific one). */
    public boolean requiresAtLeastOne() {
        return this == SERIAL_OR_IMEI;
    }
}
