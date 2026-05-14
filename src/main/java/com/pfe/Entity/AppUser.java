package com.pfe.Entity;

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

    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    @Builder.Default
    private boolean enabled = false;

    private Instant validatedAt;
    private String validatedBy;
    private String rejectionReason;

    private String password;

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

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
