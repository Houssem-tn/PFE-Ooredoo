package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsQualitySliceResponse(
        String label,
        int caseCount,
        Double averageDebitDl,
        Double averageDebitUl,
        Double averageRsrp,
        Double averageRsrq,
        Double averageSinr,
        Double averageMetrageCable,
        Double averageNombreDePv
) {
}
