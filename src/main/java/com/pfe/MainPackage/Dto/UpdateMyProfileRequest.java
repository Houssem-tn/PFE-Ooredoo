package com.pfe.MainPackage.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update payload. Only the fields a user is allowed to
 * change about themselves are present here. Sensitive attributes (email /
 * username, role, account status, permissions) are intentionally excluded so
 * they can never be modified through the "edit my profile" flow.
 */
public record UpdateMyProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 3, max = 120, message = "Full name must be between 3 and 120 characters")
        String fullName,

        // Optional. Empty is allowed (clears the phone); otherwise must look like a phone number.
        @Pattern(
                regexp = "^\\s*$|^[+]?[0-9][0-9\\s().\\-]{5,19}$",
                message = "Phone number is invalid"
        )
        String phone
) {}
