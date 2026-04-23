package com.pfe.case_management.entity;

import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.model.CaseTimelineEvent;
import com.pfe.case_management.model.TechnicalMetrics;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

import com.pfe.case_management.dto.CaseProofFileResponse;

@Document(collection = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseEntity {

    @Id
    private String id;

    // Case type
    private CaseType type;

    // External CRM info
    private String crmCase;
    private Instant dateCreationCrm;
    private Instant openingDateTimos;

    // Workflow status
    private CaseStatus status;

    // Client
    private String clientName;
    private String clientPhone;
    private String msisdn;
    private String commentaire;

    // Location
    private String latitude;
    private String longitude;
    private String localite;
    private String delegation;
    private String gouvernorat;
    private String siteSecteur;
    private String ownerZmId;
    private String ownerZmName;
    private String assignedSttId;

    // Commercial / offer
    private String offre;
    private String desPack;

    // Assignment: ZM
    private String zmId;
    private String zmEmail;
    private String zmName;
    private Instant dateAffectationZm;

    // Assignment: STT
    private String sttId;
    private String sttEmail;
    private String sttName;
    private Instant dateAffectationStt;

    // Execution
    private String executorId;
    private ExecutorRole executorRole;
    private ExecutionMode executionMode;

    // Imported source snapshot
    private String sourceAssignedSttName;
    private String sourceStatut;
    private String sourceRepTravauxStt;
    private String sourceGovernorate;
    private Instant sourceLastImportedAt;

    // Assignment: TECH
    private String techId;
    private String techEmail;
    private String techName;
    private Instant dateAffectationTech;

    // Business blocks
    private RdvInfo rdv;
    private SlaTrackingInfo slaTracking;
    private List<CaseTimelineEvent> timeline;
    private WorkInfo work;
    private ZmValidation zmValidation;
    private TechnicalMetrics metrics;

    @Transient
    private List<CaseProofFileResponse> workProofs;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}
