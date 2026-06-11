package com.pfe.case_management.dto;

/**
 * Summarises the outcome of a bulk PLT/RES field-import handled by
 * {@code CaseService.bulkImportFieldImportComplaintCases()}.
 */
public record BulkPltImportResult(
        int createdCount,
        int duplicateCount,
        int invalidCount
) {
}
