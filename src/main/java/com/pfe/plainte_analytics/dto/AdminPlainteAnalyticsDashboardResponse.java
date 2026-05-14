package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsDashboardResponse(
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
        String bestStt,
        String worstStt,
        String bestGovernorate,
        String worstGovernorate,
        String mostDelayedStage,
        Double averageDebitDl,
        Double averageDebitUl,
        Double averageRsrp,
        Double averageRsrq,
        Double averageSinr,
        Double averageMetrageCable,
        Double averageNombreDePv,
        List<AdminPlainteAnalyticsSttItemResponse> sttRanking,
        List<AdminPlainteAnalyticsGovernorateItemResponse> governorateBreakdown,
        List<AdminPlainteAnalyticsCauseItemResponse> topInjoignabiliteCauses,
        List<AdminPlainteAnalyticsCauseItemResponse> topAbandonCauses,
        List<AdminPlainteAnalyticsPipelineStageResponse> pipelineStages,
        List<AdminPlainteAnalyticsQualitySliceResponse> qualityByStt,
        List<AdminPlainteAnalyticsQualitySliceResponse> qualityByGovernorate,
        List<AdminPlainteAnalyticsAlertResponse> alerts,
        List<String> insights
) {
}
