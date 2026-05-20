package com.pfe.merged_analytics.dto;

import java.util.List;
import java.util.Map;

public record MergedAnalyticsSummaryResponse(
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int warningRows,
        Map<String, Long> countBySource,
        Map<String, Long> countBySourceLabel,
        Map<String, Long> countByZoneManager,
        Map<String, Long> countByStt,
        Map<String, Long> countByStatus,
        Map<String, Long> countByGouvernorat,
        String dateMin,
        String dateMax,
        List<String> missingOptionalColumns
) {}
