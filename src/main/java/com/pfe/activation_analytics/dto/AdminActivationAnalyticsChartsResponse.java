package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsChartsResponse(
        String bestStt,
        String worstStt,
        String bestGovernorate,
        String worstGovernorate,
        String mostDelayedStage,
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
