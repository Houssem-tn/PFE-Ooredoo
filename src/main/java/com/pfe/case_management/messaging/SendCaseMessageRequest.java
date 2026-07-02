package com.pfe.case_management.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCaseMessageRequest(
        @NotBlank(message = "Message content is required")
        @Size(max = 4000, message = "Message content must be at most 4000 characters")
        String content
) {
}
