package com.pfe.case_management.dto;

public record ZmTodoAnalyticsSttItemResponse(
        String sttName,
        long caseCount,
        double percentage,
        Double averageSlaBusinessDays,
        long delayedCases,
        double delayedRate,
        long geleCases,
        double geleRate
) {
}
