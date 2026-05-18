package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DpmTimelineEvent;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_dpm_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmRequest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String dpmNumber;

    private String zmId;
    private String zmName;
    private String zmEmail;

    private String sttId;
    private String sttName;
    private String sttEmail;

    @Builder.Default
    private DpmStatus status = DpmStatus.WAITING_TEAM_STOCK_APPROVAL;

    @Builder.Default
    private List<DpmItem> items = new ArrayList<>();

    private String notes;
    private String deliveryPersonName;

    // ── External Team Stock approval fields ────────────────────────────────────
    private String externalApprovalReference;
    private String externalApprovalNote;
    private Instant externalApprovalMarkedAt;
    private String externalApprovalMarkedByZmId;

    // ── RDV (Rendez-vous) fields ───────────────────────────────────────────────
    private String rdvDate;
    private String rdvLocation;
    private String rdvNote;
    private Instant rdvScheduledAt;
    private String rdvScheduledByZmId;

    // ── Rejection fields ───────────────────────────────────────────────────────
    private String externalRejectionReason;
    private Instant externalRejectedAt;

    @CreatedDate
    private Instant createdAt;

    private Instant confirmedAt;

    @Builder.Default
    private List<DpmTimelineEvent> timeline = new ArrayList<>();
}
