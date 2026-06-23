package com.pfe.case_management.model;

/**
 * Identifies which bulk operation produced an aggregated case-assignment notification.
 * Used to phrase the single summary alert a ZM receives instead of hundreds of per-case alerts.
 *
 * <ul>
 *   <li>{@code INJECTION}   — a ZM injected their own field work (PLT/RES Excel import).</li>
 *   <li>{@code DISTRIBUTION}— Admin distributed imported cases that were routed to this ZM.</li>
 * </ul>
 */
public enum BulkCaseOperationType {
    INJECTION,
    DISTRIBUTION
}
