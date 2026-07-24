package com.pfe.stock_management.retrieved_stock;

/**
 * Lifecycle of a {@link RetrievedStockItem}.
 *
 * <p><b>Correct business flow:</b> Customer → Technician Retrieved Stock → STT Collected Stock →
 * Material Return. The technician declares recovery, the responsible STT physically collects it,
 * then the STT returns it through the existing Material Return workflow.
 *
 * <p><b>Backward compatibility:</b> the two legacy values {@link #PENDING_COLLECTION} and
 * {@link #COLLECTED} predate this correction (they modelled a ZM-collection step). They are kept so
 * historical MongoDB documents keep deserializing and reading correctly:
 * {@code PENDING_COLLECTION} is treated as equivalent to {@link #PENDING_STT_COLLECTION}, and
 * {@code COLLECTED} is a terminal historical ZM-collected state surfaced in read-only tracking.
 * New declarations always use {@link #PENDING_STT_COLLECTION}; new collections use
 * {@link #COLLECTED_BY_STT}.
 */
public enum RetrievedStockItemStatus {

    /** @deprecated legacy pending state (ZM era). Equivalent to {@link #PENDING_STT_COLLECTION}. */
    @Deprecated
    PENDING_COLLECTION,

    /** @deprecated legacy terminal state (ZM collected). Preserved for historical documents. */
    @Deprecated
    COLLECTED,

    /** Retrieved from the client, held by the technician, waiting for the responsible STT to collect. */
    PENDING_STT_COLLECTION,

    /** Physically collected by the responsible STT — now sits in STT Collected Stock. */
    COLLECTED_BY_STT,

    /** Added to an active Material Return Request (not yet completed). */
    IN_RETURN_REQUEST,

    /** Returned through the Material Return workflow. Terminal state. */
    RETURNED;

    /** True for any state where the item is still awaiting STT collection (new or legacy). */
    public boolean isPendingCollection() {
        return this == PENDING_STT_COLLECTION || this == PENDING_COLLECTION;
    }

    /** True once physically collected by an STT (or legacy ZM collection) and not yet returned. */
    public boolean isCollected() {
        return this == COLLECTED_BY_STT || this == COLLECTED;
    }
}
