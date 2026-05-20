package com.pfe.merged_analytics.dto;

import java.util.List;

public record MergedAnalyticsInvalidRowsPageResponse(
        List<MergedAnalyticsInvalidRowDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
