package com.pfe.case_management.dto;

import java.time.Instant;

public record ZmFieldWorkImportResponse(
        String importBatchId,
        String sourceFileName,
        Instant uploadedAt,
        String businessType,
        /** "COMPLAINT_CASES", "ACTIVATION_CASES", or "FIELD_TODOS" depending on the import path */
        String target,
        int totalRows,
        int matchedConnectedZmRows,
        int enCoursRows,
        int createdWorkTodoCount,
        int createdComplaintCasesCount,
        int createdActivationCasesCount,
        int duplicateCount,
        int ignoredOtherZmCount,
        int ignoredStatusCount,
        int invalidRowsCount,
        String message,
        String analysisId,
        String dispatchMode,
        String dispatchModeUserName
) {
}
