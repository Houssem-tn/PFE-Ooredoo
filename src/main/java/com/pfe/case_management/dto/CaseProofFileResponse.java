package com.pfe.case_management.dto;

import java.time.Instant;

public record CaseProofFileResponse(
        String fileId,
        String originalFilename,
        String contentType,
        long size,
        String uploadedBy,
        Instant createdAt,
        String viewUrl,
        String downloadUrl,
        Double locationLatitude,
        Double locationLongitude,
        Instant locationCapturedAt,
        Double locationAccuracyMeters,
        String captureSource
) {
}
