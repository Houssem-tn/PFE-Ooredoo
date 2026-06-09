package com.pfe.case_management.dto;

import java.time.Instant;

public record ZmFieldWorkImportResponse(
        String importBatchId,
        String sourceFileName,
        Instant uploadedAt,
        String businessType,
        /** "COMPLAINT_CASES" for PLT, "FIELD_TODOS" for RES and future types */
        String target,
        int totalRows,
        int matchedConnectedZmRows,
        int enCoursRows,
        int createdWorkTodoCount,
        int createdComplaintCasesCount,
        int duplicateCount,
        int ignoredOtherZmCount,
        int ignoredStatusCount,
        int invalidRowsCount,
        String message,
        String analysisId
) {
}
