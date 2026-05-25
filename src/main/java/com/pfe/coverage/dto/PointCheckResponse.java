package com.pfe.coverage.dto;

import java.util.List;

public record PointCheckResponse(
        double latitude,
        double longitude,
        boolean inside4G,
        boolean inside5G,
        boolean insideWs4G,
        boolean insideWs5G,
        String decision,
        List<String> matchedLayerNames
) {}
