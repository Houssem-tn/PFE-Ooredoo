package com.pfe.coverage.dto;

import java.time.Instant;
import java.util.List;

public record CoverageAnalysisResultResponse(
        String id,
        String caseId,
        String crmCase,
        String caseType,
        String governorate,
        double latitude,
        double longitude,
        boolean inside4G,
        boolean inside5G,
        boolean insideWs4G,
        boolean insideWs5G,
        List<String> matchedLayerIds,
        String decision,
        String analyzedByEmail,
        Instant analyzedAt
) {}
