package com.pfe.case_management.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignTechRequest(
        @NotBlank(message = "techId is required")
        String techId
) {}