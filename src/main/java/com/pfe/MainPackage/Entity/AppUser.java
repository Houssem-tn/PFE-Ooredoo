package com.pfe.MainPackage.Entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    private String id;

    @Indexed
    private String fullName;

    @Indexed(unique = true)
    private String email;

    private String phone;

    private UserRole roleRequested;

    @Indexed
    private String assignedSttId;

    private String assignedSttEmail;

    @Indexed
    private String assignedZmId;

    private String assignedZmEmail;

    @Builder.Default
    private List<String> assignedGovernorates = new ArrayList<>();

    @Builder.Default
    private List<String> coveredGovernorates = new ArrayList<>();

    @Builder.Default
    private List<String> requestedGovernorates = new ArrayList<>();

    @Builder.Default
    private Map<String, List<String>> coverageByModule = new HashMap<>();

    // STT-scoped, per-ZM coverage confirmation state. Keyed by ZM id → governorates
    // that ZM has approved / declined for this STT. This replaces the old one-to-one
    // assignedZm link: several ZMs (each owning the same governorate for a different
    // module) can now decide independently, and one ZM's decision never touches another's.
    @Builder.Default
    private Map<String, List<String>> coverageApprovedByZm = new HashMap<>();

    @Builder.Default
    private Map<String, List<String>> coverageDeclinedByZm = new HashMap<>();

    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    @Builder.Default
    private boolean enabled = false;

    private Instant validatedAt;
    private String validatedBy;
    private String rejectionReason;

    private String password;

    // Set whenever the password changes (e.g. via a completed password reset).
    // Stored for audit/notification display only — it is NOT used to validate
    // JWTs (tokenVersion is the single source of truth for invalidation). Legacy
    // users predating this field simply have null here and keep working.
    private Instant passwordChangedAt;

    // Monotonic session/JWT epoch. Every issued access token embeds the value
    // current at issue time (JWT "ver" claim); a password reset increments it so
    // all previously issued tokens (REST and WebSocket) stop validating at once.
    // Primitive long so legacy Mongo documents without the field deserialize to
    // 0, which matches the 0 default embedded in tokens issued before this field
    // existed — existing logins are therefore never invalidated by the upgrade.
    @Builder.Default
    private long tokenVersion = 0L;

    private UserRole role;

    private CrmBoutiqueProfile crmBoutiqueProfile;
    private SttProfile sttProfile;
    private TechnicianProfile technicianProfile;
    private ZmProfile zmProfile;

    private Instant lastLoginAt;

    private String profileImageFileId;
    private String profileImageUrl;
    private String profileThumbnailUrl;

    @Builder.Default
    private boolean canCreateDpm = false;

    @Builder.Default
    private boolean selfExecutor = false;

    @Builder.Default
    private boolean canImportCoverage = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
