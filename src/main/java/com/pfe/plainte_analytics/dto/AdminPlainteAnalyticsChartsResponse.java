package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsChartsResponse(
        String bestStt,
        String worstStt,
        String bestGovernorate,
        String worstGovernorate,
        String mostDelayedStage,
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
