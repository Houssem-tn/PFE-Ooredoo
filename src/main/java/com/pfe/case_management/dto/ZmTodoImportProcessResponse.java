package com.pfe.case_management.dto;

import java.time.Instant;

public record ZmTodoImportProcessResponse(
        String analysisId,
        String sourceFileName,
        Instant uploadedAt,
        String importBatchId,
        int totalRows,
        int matchedRows,
        int ignoredRows,
        int validMatchedRows,
        int invalidMatchedRows,
        int savedRows
) {
}
