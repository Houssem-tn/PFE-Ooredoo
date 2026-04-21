package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsRowsPageResponse(
        List<AdminActivationAnalyticsRowListItemResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
