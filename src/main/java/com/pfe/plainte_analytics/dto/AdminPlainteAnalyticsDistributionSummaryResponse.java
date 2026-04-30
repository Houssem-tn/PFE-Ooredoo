package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsDistributionSummaryResponse(
        int processableRows,
        int actionableRows,
        int createdCases,
        int updatedExistingCases,
        int assignedToZmCases,
        int sentToAdminQueueCases,
        int sourceCompletedRows,
        int sourceTerminatedRows,
        int noGovernorateRows
) {
}
