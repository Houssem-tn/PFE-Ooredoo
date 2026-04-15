package com.pfe.case_management.dto;

public record ZmPerformanceSttBreakdownItemResponse(
        String sttName,
        long caseCount,
        double percentage,
        Double averageSlaBusinessDays
) {
}
