package com.pfe.case_management.dto;

import java.time.Instant;
import java.util.List;

public record ImportedTodoListItemResponse(
        String id,
        String importBatchId,
        String sourceFileName,
        String managerFilterUsed,
        Integer rowNumber,
        Boolean validRow,
        List<String> validationIssues,
        String crmCase,
        String fxDateCrm,
        String gouvernorat,
        String description,
        String stt,
        String statut,
        String zoneManager,
        Instant uploadedAt,
        String businessType,
        String workflowStatus,
        Boolean workCreated,
        Boolean hasValidCoordinates,
        String msisdn
) {
}
