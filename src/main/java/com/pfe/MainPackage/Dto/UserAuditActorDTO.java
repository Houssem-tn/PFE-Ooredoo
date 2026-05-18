package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.UserRole;

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
