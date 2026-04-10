package com.pfe.case_management.dto;

import java.util.List;

public record ImportedTodoPageResponse(
        List<ImportedTodoListItemResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
