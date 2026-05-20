package com.pfe.merged_analytics.dto;

public record MergedAnalyticsUploadResponse(
        String studyId,
        String status,
        String message
) {}
