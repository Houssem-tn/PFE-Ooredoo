package com.pfe.activation_analytics.dto;

import com.pfe.activation_analytics.model.ActivationImportProcessingMode;

import java.time.Instant;

public record AdminActivationAnalyticsImportResponse(
        ActivationImportProcessingMode mode,
        String batchId,
        String sourceFileName,
        int totalRows,
        int processableRows,
        int insertedRows,
        int validNewRows,
        int invalidRows,
        int duplicateRows,
        int distinctStts,
        int distinctGovernorates,
        int enCoursRows,
        int effectueRows,
        int termineRows,
        int actionableRows,
        AdminActivationAnalyticsDistributionSummaryResponse distributionSummary,
        Instant uploadedAt
) {
}
