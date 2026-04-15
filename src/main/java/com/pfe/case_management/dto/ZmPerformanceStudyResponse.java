package com.pfe.case_management.dto;

import java.util.List;

public record ZmPerformanceStudyResponse(
        String selectedZoneManager,
        String selectedGovernorate,
        List<String> availableZoneManagers,
        List<String> availableGovernorates,
        long totalCases,
        long rowsWithSla,
        double averageSlaBusinessDays,
        Double minSlaBusinessDays,
        Double maxSlaBusinessDays,
        long distinctSttCount,
        long distinctGovernorateCount,
        String topSttName,
        long topSttCaseCount,
        String topGovernorateName,
        long topGovernorateCaseCount,
        String bestGovernorateName,
        Double bestGovernorateAverageSla,
        String worstGovernorateName,
        Double worstGovernorateAverageSla,
        List<String> insights,
        List<ZmPerformanceSttBreakdownItemResponse> sttBreakdown,
        List<ZmPerformanceGovernorateItemResponse> governorateBreakdown
) {
}
