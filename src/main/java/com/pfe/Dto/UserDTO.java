package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.UserRole;

import java.time.Instant;

public record UserDTO(
        String id,
        String fullName,
        String email,
        String phone,

        UserRole role,
        UserRole roleRequested,

        AccountStatus status,
        boolean enabled,

        Instant validatedAt,
        String validatedBy,
        String rejectionReason,

        Instant createdAt,
        Instant lastLoginAt,

        String assignedSttId,
        String assignedSttEmail,

        String assignedZmId,
        String assignedZmEmail,

        String profileImageFileId,
        String profileImageUrl,
        String profileThumbnailUrl
) {}