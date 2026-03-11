package com.pfe.Dto;

import com.pfe.Entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 60) String firstName,
        @NotBlank @Size(min = 2, max = 60) String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 120) String password,
        UserRole role,
        String sttId,
        String phone
) {}