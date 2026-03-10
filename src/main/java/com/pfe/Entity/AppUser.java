package com.pfe.Entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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

    // requested role at registration (STT / TECH / ZM)
    private UserRole roleRequested;

    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    // disable login until approved
    @Builder.Default
    private boolean enabled = false;

    // audit
    private Instant validatedAt;
    private String validatedBy;      // approver email
    private String rejectionReason;

    private String password;

    private UserRole role;

    private CrmBoutiqueProfile crmBoutiqueProfile;
    private SttProfile sttProfile;
    private TechnicianProfile technicianProfile;
    private ZmProfile zmProfile;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}