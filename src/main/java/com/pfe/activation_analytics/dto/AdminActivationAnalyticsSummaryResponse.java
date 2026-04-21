package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsSummaryResponse(
        long storedRows,
        long validStoredRows,
        long batchCount,
        AdminActivationAnalyticsBatchResponse latestBatch,
        List<AdminActivationAnalyticsBatchResponse> recentBatches,
        List<String> availableStts,
        List<String> availableGovernorates,
        String selectedStt,
        String selectedGovernorate,
        AdminActivationAnalyticsKpiResponse kpis,
        double rdvSuccessRate,
        Double averageDebitDl,
        Double averageDebitUl,
        Double averageRsrp,
        Double averageRsrq,
        Double averageSinr,
        Double averageMetrageCable,
        Double averageNombreDePv
) {
}
