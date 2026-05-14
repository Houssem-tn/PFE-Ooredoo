package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsDistributionSummaryResponse(
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
