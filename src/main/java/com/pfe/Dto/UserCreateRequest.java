package com.pfe.Dto;

import com.pfe.Entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 120) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 120) String password,
        @NotNull UserRole role,
        Boolean enabled
) {}
