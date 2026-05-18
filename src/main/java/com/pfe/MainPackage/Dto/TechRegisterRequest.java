package com.pfe.MainPackage.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TechRegisterRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 2, max = 60) String firstName,
        @NotBlank @Size(min = 2, max = 60) String lastName,
        @NotBlank @Size(min = 6, max = 120) String password,
        String phone
) {}