package com.pfe.activation_analytics.dto;

import java.time.Instant;
import java.util.List;

public record SavedActivationAnalyticsStudyResponse(
        String id,
        Instant savedAt,
        String savedBy,
        List<String> sourceFileNames,
        List<String> sourceBatchIds,
        AdminActivationAnalyticsDashboardResponse dashboard
) {
}
