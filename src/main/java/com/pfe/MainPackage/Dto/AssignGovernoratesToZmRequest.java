package com.pfe.MainPackage.Dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AssignGovernoratesToZmRequest(
        @NotBlank String zmId,
        List<@NotBlank String> assignedGovernorates
) {
}
