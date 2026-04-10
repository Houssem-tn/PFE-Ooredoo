package com.pfe.case_management.dto;

import java.time.Instant;

public record ImportedTodoListItemResponse(
        String id,
        String importBatchId,
        String sourceFileName,
        String managerFilterUsed,
        String crmCase,
        String fxDateCrm,
        String gouvernorat,
        String description,
        String stt,
        String statut,
        String zoneManager,
        Instant uploadedAt
) {
}
