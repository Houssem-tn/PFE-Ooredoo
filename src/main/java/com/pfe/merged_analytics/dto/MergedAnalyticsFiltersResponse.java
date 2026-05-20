package com.pfe.merged_analytics.dto;

import java.util.List;

public record MergedAnalyticsFiltersResponse(
        List<String> sources,
        List<String> zoneManagers,
        List<String> stts,
        List<String> governorates,
        List<String> statuses) {}
