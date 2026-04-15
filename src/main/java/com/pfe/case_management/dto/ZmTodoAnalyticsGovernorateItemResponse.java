package com.pfe.case_management.dto;

public record ZmTodoAnalyticsGovernorateItemResponse(
        String gouvernorat,
        long caseCount,
        double percentage,
        Double averageSlaBusinessDays,
        long delayedCases,
        double delayedRate,
        long geleCases,
        double geleRate,
        String topSttName,
        long topSttCaseCount
) {
}
