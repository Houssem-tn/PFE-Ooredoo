package com.pfe.case_management.dto;

import java.util.List;

public record ImportedTodoPreviewRowResponse(
        int rowNumber,
        String crmCase,
        String fxDateCrm,
        String contact1,
        String contact2,
        String msisdn,
        String gouvernorat,
        String description,
        String latitude,
        String longitude,
        String openDateSurTimos,
        String dateAffectationStt,
        String stt,
        String statut,
        String zoneManager,
        List<String> issues,
        boolean readyToInject
) {
}
