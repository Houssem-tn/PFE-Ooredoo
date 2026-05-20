package com.pfe.merged_analytics.dto;

public record MergedAnalyticsStatusResponse(
        String studyId,
        String processingStatus,
        int totalRows,
        int processedRows,
        int validRows,
        int invalidRows,
        int warningRows,
        int progressPercent,
        Long processingDurationMs,
        String errorMessage
) {}
