package com.pfe.Dto;

import jakarta.validation.constraints.NotBlank;

public record AssignSttToZmRequest(
        @NotBlank String sttId,
        @NotBlank String zmId
) {

}
