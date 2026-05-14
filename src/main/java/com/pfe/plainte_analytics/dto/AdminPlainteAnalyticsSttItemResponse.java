package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsSttItemResponse(
        String sttName,
        int caseCount,
        double percentage,
        Double averageAssignmentSla,
        Double averageRdvDelay,
        Double averageExecutionSla,
        Double averageEndToEndSla,
        double rdvSuccessRate,
        double workCompletionRate,
        double abandonRate,
        double injoignableRate,
        double delayedRate
) {
}
