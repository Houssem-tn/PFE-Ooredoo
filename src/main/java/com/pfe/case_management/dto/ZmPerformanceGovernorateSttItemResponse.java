package com.pfe.case_management.dto;

public record ZmPerformanceGovernorateSttItemResponse(
        String sttName,
        long caseCount,
        double percentage,
        Double averageSlaBusinessDays
) {
}
