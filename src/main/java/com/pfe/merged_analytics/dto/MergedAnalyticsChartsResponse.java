package com.pfe.merged_analytics.dto;

import java.util.List;
import java.util.Map;

public record MergedAnalyticsChartsResponse(
        Map<String, Long> sourceDistribution,
        Map<String, Long> sourceLabelDistribution,
        Map<String, Long> zoneManagerDistribution,
        Map<String, Long> sttDistribution,
        Map<String, Long> statusDistribution,
        Map<String, Long> gouvernoratDistribution,
        Map<String, Long> slaCrmLabelDistribution,
        Map<String, Long> slaTechLabelDistribution,
        List<MonthlyTrendPoint> monthlyTrend,
        TechnicalMetricsAverages technicalMetrics
) {
    public record MonthlyTrendPoint(String month, long count) {}

    public record TechnicalMetricsAverages(
            Double avgDebitDl,
            Double avgDebitUl,
            Double avgRsrp,
            Double avgRsrq,
            Double avgSinr,
            Double avgSlaCrm,
            Double avgSlaTech
    ) {}
}
