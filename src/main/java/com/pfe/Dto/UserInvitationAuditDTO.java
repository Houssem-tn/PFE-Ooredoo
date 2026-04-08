package com.pfe.Dto;

import com.pfe.Entity.TechInvitationStatus;

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
