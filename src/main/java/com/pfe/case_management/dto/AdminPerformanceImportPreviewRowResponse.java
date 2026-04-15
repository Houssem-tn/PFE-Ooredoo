package com.pfe.case_management.dto;

import java.util.List;

public record AdminPerformanceImportPreviewRowResponse(
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
