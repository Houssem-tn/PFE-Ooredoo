package com.pfe.case_management.dto;

import java.time.Instant;

public record ZmTodoAnalysisListItemResponse(
        String id,
        String sourceFileName,
        Instant uploadedAt,
        String importBatchId,
        String connectedZmName,
        long totalCases,
        int matchedRows,
        int invalidMatchedRows,
        Double averageSlaBusinessDays
) {
}
