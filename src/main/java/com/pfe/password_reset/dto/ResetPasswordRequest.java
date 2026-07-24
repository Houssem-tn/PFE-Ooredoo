package com.pfe.password_reset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/reset-password}. Explicit typed DTO (never a
 * raw Map). Bean-validation here is a cheap first gate; the authoritative
 * password-policy and token checks run in the service.
 *
 * <p>The token length bound rejects clearly malformed inputs before any hashing
 * or lookup. The password {@code max} bound rejects excessively long passwords
 * to prevent BCrypt CPU/resource abuse; the {@code min} matches the shared
 * registration policy.</p>
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 20, max = 512) String token,
        @NotBlank @Size(min = 6, max = 120) String newPassword,
        @NotBlank @Size(min = 6, max = 120) String confirmPassword
) {}
