package com.pfe.case_management.dto;

/**
 * Summarises the outcome of a bulk PLT import handled by
 * {@code CaseService.bulkImportPltComplaintCases()}.
 */
public record BulkPltImportResult(
        int createdCount,
        int duplicateCount,
        int invalidCount
) {
}
