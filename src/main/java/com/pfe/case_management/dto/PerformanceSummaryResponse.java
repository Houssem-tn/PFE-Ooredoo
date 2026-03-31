package com.pfe.case_management.dto;

public record PerformanceSummaryResponse(
        String userId,
        String userName,
        String userEmail,
        String role,
        long assignedCases,
        long rdvOnTime,
        long rdvLate,
        double rdvOnTimeRate,
        long slaWarningCount,
        long slaBreachCount,
        long slaEscalationCount,
        double averageResponseMinutes,
        double averageOverdueMinutes,
        double workCompletionProgress,
        long activeTasksCount
) {
}