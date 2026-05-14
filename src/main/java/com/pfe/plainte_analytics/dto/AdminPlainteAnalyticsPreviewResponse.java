package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsPreviewResponse(
        String sourceFileName,
        int totalRows,
        int processableRows,
        int validNewRows,
        int duplicateRows,
        int invalidRows,
        int distinctStts,
        int distinctGovernorates,
        int enCoursRows,
        int effectueRows,
        int termineRows,
        int actionableRows,
        int invalidRowPageSize,
        List<AdminPlainteAnalyticsInvalidReasonResponse> invalidReasonSummaries,
        List<AdminPlainteAnalyticsPreviewRowResponse> validPreviewRows,
        List<AdminPlainteAnalyticsPreviewRowResponse> invalidRowsPreview
) {
}
