package com.pfe.case_management.dto;

import com.pfe.case_management.enums.PlainteStatus;
import com.pfe.case_management.model.ComplaintDiagnosisInfo;
import com.pfe.case_management.model.ComplaintResolutionInfo;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PlainteDTO {
    private String id;
    private String crmCase;
    private PlainteStatus status;
    private Instant dateCreationCrm;
    private Instant openingDateTimos;
    private String clientName;
    private String clientPhone;
    private String contactCl2;
    private String msisdn;
    private String commentaire;
    private String complaintDetail;
    private String complaintDescription;
    private String gouvernorat;
    private String delegation;
    private String localite;
    private String latitude;
    private String longitude;
    private String offre;
    private String desPack;
    private String typeOffreSpeed;
    private String typeClient;
    private String source;
    private Instant openDateActivation;
    private String zmName;
    private String sttName;
    private String techName;
    private RdvInfo rdv;
    private ComplaintDiagnosisInfo diagnosis;
    private WorkInfo work;
    private ComplaintResolutionInfo resolution;
    private ZmValidation zmValidation;
    private Instant createdAt;
    private Instant updatedAt;
}
