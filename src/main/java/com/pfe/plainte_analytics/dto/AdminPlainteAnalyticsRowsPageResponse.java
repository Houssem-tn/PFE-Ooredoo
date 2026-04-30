package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsRowsPageResponse(
        List<AdminPlainteAnalyticsRowListItemResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
