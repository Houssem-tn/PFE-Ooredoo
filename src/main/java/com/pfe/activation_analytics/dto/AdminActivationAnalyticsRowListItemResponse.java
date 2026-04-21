package com.pfe.activation_analytics.dto;

import java.time.Instant;

public record AdminActivationAnalyticsRowListItemResponse(
        String id,
        String batchId,
        Instant uploadedAt,
        String sourceFileName,
        Integer rowNumber,
        String crmCase,
        String gouvernorat,
        String stt,
        String statut,
        Integer assignmentBusinessDays,
        Integer rdvBusinessDays,
        Integer executionBusinessDays,
        Integer endToEndBusinessDays
) {
}
