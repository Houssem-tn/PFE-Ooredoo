package com.pfe.merged_analytics.dto;

import java.time.Instant;
import java.util.List;

public record MergedAnalyticsStudyListItem(
        String studyId,
        String fileName,
        String fileType,
        Instant uploadedAt,
        int totalRows,
        int validRows,
        int invalidRows,
        String processingStatus,
        Long processingDurationMs,
        List<String> sourcesFound
) {}
