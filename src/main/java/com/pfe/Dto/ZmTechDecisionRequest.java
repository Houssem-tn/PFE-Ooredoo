package com.pfe.Dto;

import jakarta.validation.constraints.NotNull;

public record ZmTechDecisionRequest(
        @NotNull(message = "Approve flag is required")
        Boolean approve
) {
}