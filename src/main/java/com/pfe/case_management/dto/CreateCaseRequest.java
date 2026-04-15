package com.pfe.case_management.dto;

import com.pfe.case_management.enums.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateCaseRequest(
        @NotNull(message = "type is required")
        CaseType type,

        @NotBlank(message = "crmCase is required")
        String crmCase,

        Instant dateCreationCrm,
        Instant openingDateTimos,

        String clientName,
        String clientPhone,
        String msisdn,
        String commentaire,

        String latitude,
        String longitude,
        String localite,
        String delegation,
        @NotBlank(message = "gouvernorat is required")
        String gouvernorat,
        String siteSecteur,

        String offre,
        String desPack
) {}
