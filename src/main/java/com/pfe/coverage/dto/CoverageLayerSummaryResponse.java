package com.pfe.coverage.dto;

import com.pfe.coverage.enums.CoverageLayerStatus;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;

import java.time.Instant;

public record CoverageLayerSummaryResponse(
        String id,
        String name,
        CoverageTechnology technology,
        CoverageLayerType layerType,
        String governorate,
        String sourceFileName,
        String color,
        String uploadedByEmail,
        CoverageLayerStatus status,
        Instant createdAt
) {}
