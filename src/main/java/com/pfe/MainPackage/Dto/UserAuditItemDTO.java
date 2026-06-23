package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.UserRole;

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