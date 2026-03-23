package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.UserRole;

import java.time.Instant;

public record UserAuditItemDTO(
        String id,
        String fullName,
        String email,
        UserRole role,
        AccountStatus status,
        boolean enabled,
        Instant createdAt,
        Instant lastLoginAt
) {}