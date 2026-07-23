package com.pfe.stock_management.retrieved_stock;

/**
 * What actually happened when a technician tried to retrieve one expected piece of equipment from
 * a customer during a PLT (complaint) or RES (résiliation) intervention.
 *
 * <p>Only {@link #RETRIEVED} and {@link #DAMAGED} represent equipment the technician is physically
 * holding — those two (and only those two) materialise a {@link RetrievedStockItem} pending ZM
 * collection. The other three are declared for audit/visibility but create no stock record: there
 * is nothing to collect.
 */
public enum RecoveryDisposition {
    /** Physically retrieved from the client, in usable/acceptable condition. */
    RETRIEVED,
    /** Physically retrieved from the client, but damaged/broken. */
    DAMAGED,
    /** The client no longer has the equipment / it could not be located. */
    MISSING,
    /** The equipment was never found at the site. */
    NOT_FOUND,
    /** The client states the equipment was already returned through another channel. */
    ALREADY_RETURNED;

    /** Whether this disposition means the technician is physically holding the unit. */
    public boolean isPhysicallyHeld() {
        return this == RETRIEVED || this == DAMAGED;
    }
}
