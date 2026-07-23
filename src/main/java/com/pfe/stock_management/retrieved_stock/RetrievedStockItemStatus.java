package com.pfe.stock_management.retrieved_stock;

/**
 * Lifecycle of a {@link RetrievedStockItem}. Deliberately binary — unlike
 * {@code MaterialReturnStatus} this workflow has no schedule-negotiation states: a ZM selects
 * pending items and confirms collection directly.
 */
public enum RetrievedStockItemStatus {
    /** Retrieved from the client, held by the technician, waiting for the ZM to physically collect it. */
    PENDING_COLLECTION,
    /** The ZM has physically collected the item. Terminal state. */
    COLLECTED
}
