package com.pfe.case_management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;



public record AssignSttRequest(

        @NotBlank(message = "STT id is required")
        String sttId
) {
}