package com.pfe.plainte_analytics.dto;

import java.time.Instant;
import java.util.List;

public record SavedPlainteAnalyticsStudyResponse(
        String id,
        Instant savedAt,
        String savedBy,
        List<String> sourceFileNames,
        List<String> sourceBatchIds,
        AdminPlainteAnalyticsDashboardResponse dashboard
) {
}
