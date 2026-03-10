package com.pfe.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest (
        @NotBlank @Size(min = 3, max = 300) String reason
) {}
