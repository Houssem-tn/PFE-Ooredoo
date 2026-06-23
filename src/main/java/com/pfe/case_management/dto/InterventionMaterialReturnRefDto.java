package com.pfe.case_management.dto;

/**
 * Lightweight cross-link to a material return related to this case. This is only a
 * reference badge for traceability — the full return lifecycle lives in the Stock
 * Audits module, not here.
 */
public record InterventionMaterialReturnRefDto(
        String id,
        String returnCode,
        String status,
        String reason
) {
}
