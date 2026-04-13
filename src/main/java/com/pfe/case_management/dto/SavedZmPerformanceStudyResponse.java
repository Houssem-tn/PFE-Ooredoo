package com.pfe.case_management.dto;

import java.time.Instant;
import java.util.List;

public record SavedZmPerformanceStudyResponse(
        String id,
        Instant savedAt,
        String savedBy,
        List<String> sourceFileNames,
        List<String> sourceBatchIds,
        ZmPerformanceStudyResponse study
) {
}
