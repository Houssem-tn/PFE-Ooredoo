package com.pfe.MainPackage.Entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tech_invitations")
public class TechInvitation {

    @Id
    private String id;

    private String email;

    private String sttId;
    private String sttEmail;
    private String sttName;

    private String zmId;
    private String zmEmail;
    private String zmName;

    private String registeredTechId;

    private String token;

    @Builder.Default
    private boolean used = false;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant registeredAt;
    private Instant zmDecisionAt;

    @Builder.Default
    private TechInvitationStatus status = TechInvitationStatus.PENDING_REGISTRATION;
}