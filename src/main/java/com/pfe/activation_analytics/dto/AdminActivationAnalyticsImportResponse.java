package com.pfe.activation_analytics.dto;

import java.time.Instant;

public record AdminActivationAnalyticsImportResponse(
        String batchId,
        String sourceFileName,
        int totalRows,
        int insertedRows,
        int validNewRows,
        int invalidRows,
        int duplicateRows,
        int distinctStts,
        int distinctGovernorates,
        Instant uploadedAt
) {
}
