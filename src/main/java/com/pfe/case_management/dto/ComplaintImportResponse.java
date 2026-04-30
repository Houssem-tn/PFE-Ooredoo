package com.pfe.case_management.dto;

import java.util.List;

public record ComplaintImportResponse(
        String sourceFileName,
        int totalRows,
        int createdCases,
        int updatedCases,
        int skippedRows,
        List<String> warnings
) {
}
