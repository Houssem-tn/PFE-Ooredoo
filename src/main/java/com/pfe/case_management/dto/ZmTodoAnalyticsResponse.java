package com.pfe.case_management.dto;

import java.util.List;

public record ZmTodoAnalyticsResponse(
        String sourceFileName,
        String connectedZmName,
        String managerNameUsed,
        int totalRows,
        int matchedRows,
        int ignoredRows,
        int validMatchedRows,
        int invalidMatchedRows,
        long totalCases,
        long rowsWithSla,
        Double averageSlaBusinessDays,
        long delayedCases,
        double delayedRate,
        long geleCases,
        double geleRate,
        long distinctSttCount,
        long distinctGovernorateCount,
        String topSttName,
        long topSttCaseCount,
        Double topSttPercentage,
        String bestGovernorateName,
        Double bestGovernorateAverageSla,
        String worstGovernorateName,
        Double worstGovernorateAverageSla,
        List<ZmTodoAnalyticsAlertResponse> alerts,
        List<String> insights,
        List<ZmTodoAnalyticsSttItemResponse> sttBreakdown,
        List<ZmTodoAnalyticsGovernorateItemResponse> governorateBreakdown,
        List<ZmTodoAnalyticsInvalidReasonResponse> invalidReasonSummaries
) {
}
