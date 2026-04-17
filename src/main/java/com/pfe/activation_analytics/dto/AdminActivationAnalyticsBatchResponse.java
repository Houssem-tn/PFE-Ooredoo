package com.pfe.activation_analytics.dto;

import java.time.Instant;

public record AdminActivationAnalyticsBatchResponse(
        String batchId,
        String fileName,
        Instant uploadedAt,
        int totalRows,
        int insertedRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int distinctStts,
        int distinctGovernorates
) {
}
