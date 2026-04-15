package com.pfe.case_management.dto;

public record ZmTodoAnalyticsAlertResponse(
        String severity,
        String title,
        String message
) {
}
