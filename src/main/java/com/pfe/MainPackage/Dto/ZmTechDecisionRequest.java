package com.pfe.MainPackage.Dto;

import jakarta.validation.constraints.NotNull;

public record ZmTechDecisionRequest(
        @NotNull(message = "Approve flag is required")
        Boolean approve
) {
}