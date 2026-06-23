package com.pfe.merged_analytics.dto;

import java.util.List;

public record MergedAnalyticsInvalidRowDto(
        String id,
        int rowNumber,
        String crmCase,
        String source,
        String zoneManager,
        List<String> reasons,
        String rawPreview
) {}
