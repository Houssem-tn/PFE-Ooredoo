package com.pfe.case_management.dto;

import java.util.List;

public record AdminPerformanceImportPreviewResponse(
        String sourceFileName,
        int totalRows,
        int validRows,
        int invalidRows,
        int distinctZoneManagers,
        int distinctStts,
        int distinctGovernorates,
        List<AdminPerformanceImportPreviewRowResponse> previewRows
) {
}
