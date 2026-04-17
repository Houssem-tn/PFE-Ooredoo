package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsDashboardResponse(
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
        List<AdminActivationAnalyticsSttItemResponse> sttRanking,
        List<AdminActivationAnalyticsGovernorateItemResponse> governorateBreakdown,
        List<AdminActivationAnalyticsCauseItemResponse> topInjoignabiliteCauses,
        List<AdminActivationAnalyticsCauseItemResponse> topAbandonCauses,
        List<AdminActivationAnalyticsPipelineStageResponse> pipelineStages,
        List<AdminActivationAnalyticsQualitySliceResponse> qualityByStt,
        List<AdminActivationAnalyticsQualitySliceResponse> qualityByGovernorate,
        List<AdminActivationAnalyticsAlertResponse> alerts,
        List<String> insights
) {
}
