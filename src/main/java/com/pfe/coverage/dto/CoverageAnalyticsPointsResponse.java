package com.pfe.coverage.dto;

import java.util.List;

/**
 * Map payload for coverage markers read from the merged-analytics dataset.
 *
 * {@code totalMatching} is the full count of rows that match the request
 * (source + valid coordinates + optional bounding box); {@code returned} is how
 * many were actually serialized after the server-side cap. When
 * {@code capped == true} the client should narrow the request (e.g. by bounding
 * box) to see the rest.
 */
public record CoverageAnalyticsPointsResponse(
        String studyId,
        String source,
        long totalMatching,
        int returned,
        boolean capped,
        List<CoverageAnalyticsPointResponse> points
) {}
