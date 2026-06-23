package com.pfe.stock_management.enums;

public enum DpmStatus {
    // ── New workflow statuses ──────────────────────────────────────────────────
    WAITING_TEAM_STOCK_APPROVAL,  // Initial state: waiting for external Team Stock approval
    TEAM_STOCK_REJECTED,          // External Team Stock rejected the request
    RDV_SCHEDULED,                // ZM recorded external approval and scheduled pickup RDV
    RDV_CONFIRMED_BY_STT,         // STT confirmed they will attend the RDV
    MATERIAL_RECEIVED,            // STT confirmed material reception (stock is updated here)
    COMPLETED,                    // All material distributed to TECHs

    // ── Legacy statuses (kept for backward compatibility) ─────────────────────
    PENDING_STT_CONFIRMATION,
    PARTIALLY_CONFIRMED,
    FULLY_CONFIRMED,
    REJECTED,
    CANCELLED
}
