package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignZmRequest(
        @NotBlank(message = "zmId is required")
        String zmId
) {}