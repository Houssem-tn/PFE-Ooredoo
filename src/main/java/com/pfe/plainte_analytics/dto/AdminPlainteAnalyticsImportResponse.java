package com.pfe.plainte_analytics.dto;

import com.pfe.plainte_analytics.model.PlainteImportProcessingMode;

import java.time.Instant;

public record AdminPlainteAnalyticsImportResponse(
        PlainteImportProcessingMode mode,
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
        AdminPlainteAnalyticsDistributionSummaryResponse distributionSummary,
        Instant uploadedAt
) {
}
