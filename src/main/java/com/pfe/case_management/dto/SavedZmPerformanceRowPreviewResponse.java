package com.pfe.case_management.dto;

import java.time.Instant;
import java.util.List;

public record SavedZmPerformanceRowPreviewResponse(
        String sourceFileName,
        Instant uploadedAt,
        int rowNumber,
        String crmCase,
        String zoneManager,
        String gouvernorat,
        String stt,
        String statut,
        String openDateSurTimos,
        String dateAffectationStt,
        Integer slaBusinessDays,
        List<String> issues,
        boolean validRow
) {
}
