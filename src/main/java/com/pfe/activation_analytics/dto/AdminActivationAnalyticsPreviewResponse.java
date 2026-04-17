package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsPreviewResponse(
        String sourceFileName,
        int totalRows,
        int validNewRows,
        int duplicateRows,
        int invalidRows,
        int distinctStts,
        int distinctGovernorates,
        int invalidRowPageSize,
        List<AdminActivationAnalyticsInvalidReasonResponse> invalidReasonSummaries,
        List<AdminActivationAnalyticsPreviewRowResponse> validPreviewRows,
        List<AdminActivationAnalyticsPreviewRowResponse> invalidRowsPreview
) {
}
