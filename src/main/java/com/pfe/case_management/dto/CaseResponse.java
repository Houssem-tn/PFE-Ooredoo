package com.pfe.case_management.dto;

import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;


import java.time.Instant;


public record CaseResponse(

        String id,
        CaseType type,
        CaseStatus status,

        // CRM
        String crmCase,
        Instant dateCreationCrm,
        Instant openingDateTimos,

        // Client
        String clientName,
        String clientPhone,
        String msisdn,

        // Location
        String localite,
        String delegation,
        String gouvernorat,

        // Assignment
        String sttName,

        // RDV
        Boolean joignable,
        Instant datePriseRdv,
        String causeInjoignabilite,
        String causeAbandon,

        // Work
        String repTravauxStt,
        Instant dateFinTrv,

        // Validation
        String repZm,

        Instant createdAt
) {
}