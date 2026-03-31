package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotNull;

public record ValidateZmRequest(

        @NotNull(message = "Approved flag is required")
        Boolean approved,

        String comment
) {
}