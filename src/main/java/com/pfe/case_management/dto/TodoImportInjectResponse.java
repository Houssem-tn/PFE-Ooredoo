package com.pfe.case_management.dto;

public record TodoImportInjectResponse(
        String importBatchId,
        String sourceFileName,
        String managerFilterUsed,
        int totalRows,
        int matchedRows,
        int unmatchedRows,
        int invalidRows,
        int duplicateRows,
        int savedRows
) {
}
