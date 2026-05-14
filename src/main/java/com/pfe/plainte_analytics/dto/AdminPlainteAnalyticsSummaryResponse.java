package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsSummaryResponse(
        long storedRows,
        long validStoredRows,
        long batchCount,
        AdminPlainteAnalyticsBatchResponse latestBatch,
        List<AdminPlainteAnalyticsBatchResponse> recentBatches,
        List<String> availableStts,
        List<String> availableGovernorates,
        String selectedStt,
        String selectedGovernorate,
        AdminPlainteAnalyticsKpiResponse kpis,
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
