package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;


public record CreateActivationCaseRequest(

        @NotBlank(message = "CRM case is required")
        String crmCase,

        Instant dateCreationCrm,
        Instant openingDateTimos,

        @NotBlank(message = "Client name is required")
        String clientName,

        @NotBlank(message = "Client phone is required")
        String clientPhone,

        String msisdn,

        String latitude,
        String longitude,
        String localite,
        String delegation,
        String gouvernorat,
        String siteSecteur
) {
}