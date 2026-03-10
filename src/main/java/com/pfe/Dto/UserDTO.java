package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.UserRole;

import java.time.Instant;

public record UserDTO(
        String id,
        String fullName,
        String email,

        // ✅ final role after approval (can be null while pending)
        UserRole role,

        // ✅ role requested at registration
        UserRole roleRequested,

        // ✅ approval status
        AccountStatus status,

        boolean enabled,

        // audit (optional but useful)
        Instant validatedAt,
        String validatedBy,
        String rejectionReason,

        Instant createdAt
) {}