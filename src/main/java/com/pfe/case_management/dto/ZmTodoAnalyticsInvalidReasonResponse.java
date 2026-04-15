package com.pfe.case_management.dto;

public record ZmTodoAnalyticsInvalidReasonResponse(
        String reason,
        long count,
        java.util.List<String> crmCases
) {
}
