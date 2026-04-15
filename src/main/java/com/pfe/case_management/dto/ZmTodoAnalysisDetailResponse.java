package com.pfe.case_management.dto;

import java.time.Instant;

public record ZmTodoAnalysisDetailResponse(
        String id,
        String sourceFileName,
        Instant uploadedAt,
        String importBatchId,
        ZmTodoAnalyticsResponse analytics
) {
}
