package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.UserRole;

public record UserAuditActorDTO(
        String id,
        String fullName,
        String email,
        UserRole role,
        AccountStatus status,
        Boolean enabled,
        String profileImageUrl,
        String profileThumbnailUrl
) {
}
