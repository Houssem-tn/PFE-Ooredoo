package com.pfe.coverage.dto;

public record CoverageAnalysisSummaryResponse(
        long totalCases,
        long analyzedCases,
        long invalidCoordinateCases,
        long inside4G,
        long inside5G,
        long insideWs4G,
        long insideWs5G,
        long outsideAllCoverage
) {}
