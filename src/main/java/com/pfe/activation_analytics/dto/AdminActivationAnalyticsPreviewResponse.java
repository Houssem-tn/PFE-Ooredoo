package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsPreviewResponse(
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
        List<AdminActivationAnalyticsInvalidReasonResponse> invalidReasonSummaries,
        List<AdminActivationAnalyticsPreviewRowResponse> validPreviewRows,
        List<AdminActivationAnalyticsPreviewRowResponse> invalidRowsPreview
) {
}
