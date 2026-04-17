package com.pfe.case_management.dto;

import java.util.List;

public record SavedZmPerformanceRowQualityResponse(
        List<String> sourceFileNames,
        long totalRows,
        long validRows,
        long invalidRows,
        long distinctZoneManagers,
        long distinctStts,
        long distinctGovernorates,
        long rowsWithSla,
        long invalidRowsWithMultipleIssues,
        int invalidPageSize,
        int invalidPage,
        long filteredInvalidRows,
        int invalidTotalPages,
        String selectedInvalidReason,
        List<SavedZmPerformanceInvalidReasonResponse> invalidReasonSummaries,
        List<SavedZmPerformanceRowPreviewResponse> invalidPreviewRows
) {
}
