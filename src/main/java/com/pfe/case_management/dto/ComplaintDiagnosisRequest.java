package com.pfe.case_management.dto;

import com.pfe.case_management.enums.ComplaintCategory;
import jakarta.validation.constraints.NotNull;

public record ComplaintDiagnosisRequest(
        @NotNull(message = "complaintCategory is required")
        ComplaintCategory complaintCategory,

        String rootCause,
        String diagnosisComment,
        boolean requiresFieldWork,
        boolean requiresTechIntervention,
        boolean requiresEscalation
) {
}
