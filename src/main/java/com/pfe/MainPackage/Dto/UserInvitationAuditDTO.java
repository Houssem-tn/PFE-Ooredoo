package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.TechInvitationStatus;

import java.time.Instant;

public record UserInvitationAuditDTO(
        String id,
        TechInvitationStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant registeredAt,
        Instant zmDecisionAt,
        boolean used
) {
}
