package com.pfe.case_management.dto;

import java.util.List;

public record TodoImportPreviewResponse(
        String sourceFileName,
        String managerFilterUsed,
        int totalRows,
        int matchedRows,
        int unmatchedRows,
        int invalidRows,
        int duplicateRows,
        int readyRows,
        List<ImportedTodoPreviewRowResponse> previewRows
) {
}
