package com.pfe.activation_analytics.dto;

import java.util.List;

public record AdminActivationAnalyticsPreviewRowResponse(
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
