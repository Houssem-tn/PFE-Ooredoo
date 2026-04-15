package com.pfe.case_management.dto;

public record AdminPerformanceImportInjectResponse(
        String batchId,
        String sourceFileName,
        int totalRows,
        int validRows,
        int invalidRows,
        int distinctZoneManagers,
        int distinctStts,
        int distinctGovernorates,
        int savedRows
) {
}
