package com.pfe.plainte_analytics.dto;

import java.util.List;

public record AdminPlainteAnalyticsPreviewRowResponse(
        int rowNumber,
        String crmCase,
        String gouvernorat,
        String stt,
        String statut,
        Integer assignmentBusinessDays,
        Integer rdvBusinessDays,
        Integer executionBusinessDays,
        Integer endToEndBusinessDays,
        List<String> issues,
        boolean validRow,
        String rowCategory
) {
}
