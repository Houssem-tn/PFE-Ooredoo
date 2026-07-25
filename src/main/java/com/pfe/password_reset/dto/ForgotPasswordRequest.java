package com.pfe.password_reset.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/forgot-password}. Only the email is accepted;
 * the response is always generic regardless of whether the account exists.
 * The {@code @Size} cap rejects absurdly long inputs cheaply before any lookup.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email
) {}
