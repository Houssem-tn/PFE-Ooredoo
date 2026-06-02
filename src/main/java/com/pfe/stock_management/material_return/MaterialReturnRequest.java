package com.pfe.stock_management.material_return;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_material_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReturnRequest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String returnCode; // e.g. RET-2026-0001

    // STT identity
    private String sttId;
    private String sttName;
    private String sttEmail;

    // Responsible ZM (resolved at creation)
    private String zmId;
    private String zmName;
    private String zmEmail;

    // Optional case link
    private String relatedCaseId;
    private String relatedCaseType; // ACT, RES, PLT, DMG
    private String crmCase;

    // Return source — determines whether STT stock is decremented on approval
    private ReturnSource returnSource;

    // Planned handover date (ISO "YYYY-MM-DD") — informational for ZM
    private String plannedReturnDate;

    // Return classification
    private ReturnReason reason;
    private ReturnCondition condition;
    private ReturnDestination destination;

    @Builder.Default
    private List<ReturnedItem> items = new ArrayList<>();

    private String comment;

    @Builder.Default
    private List<String> proofImageUrls = new ArrayList<>();

    private MaterialReturnStatus status;

    // Timestamps
    private Instant createdAt;
    private Instant submittedAt;
    private Instant reviewedAt;
    private Instant closedAt;

    // ZM review
    private String reviewedByZmId;
    private String reviewedByZmName;
    private String reviewComment;
}
