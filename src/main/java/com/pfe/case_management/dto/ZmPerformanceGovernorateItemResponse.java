package com.pfe.case_management.dto;

import java.util.List;

public record ZmPerformanceGovernorateItemResponse(
        String gouvernorat,
        long caseCount,
        double percentage,
        Double averageSlaBusinessDays,
        long distinctSttCount,
        String topSttName,
        long topSttCaseCount,
        List<ZmPerformanceGovernorateSttItemResponse> sttBreakdown
) {
}
