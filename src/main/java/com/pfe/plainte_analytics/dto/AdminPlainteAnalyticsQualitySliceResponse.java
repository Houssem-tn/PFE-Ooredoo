package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsQualitySliceResponse(
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
