package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserDTO(
        String id,
        String fullName,
        String email,
        String phone,

        UserRole role,
        UserRole roleRequested,

        AccountStatus status,
        boolean enabled,
        boolean canCreateDpm,

        Instant validatedAt,
        String validatedBy,
        String rejectionReason,

        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,

        String assignedSttId,
        String assignedSttEmail,

        String assignedZmId,
        String assignedZmEmail,
        List<String> assignedGovernorates,
        List<String> coveredGovernorates,
        List<String> requestedGovernorates,
        Map<String, List<String>> coverageByModule,

        String profileImageFileId,
        String profileImageUrl,
        String profileThumbnailUrl
) {}
