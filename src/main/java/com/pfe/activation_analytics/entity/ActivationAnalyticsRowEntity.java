package com.pfe.activation_analytics.entity;

import com.pfe.case_management.enums.CaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Document(collection = "admin_activation_performance_rows")
@CompoundIndexes({
        @CompoundIndex(
                name = "activation_rows_owner_crm_idx",
                def = "{'uploadedBy': 1, 'crmCaseNormalized': 1}"
        ),
        @CompoundIndex(
                name = "activation_rows_owner_valid_uploaded_idx",
                def = "{'uploadedBy': 1, 'validRow': 1, 'uploadedAt': -1}"
        ),
        @CompoundIndex(
                name = "activation_rows_owner_batch_row_idx",
                def = "{'uploadedBy': 1, 'batchId': 1, 'rowNumber': 1}"
        ),
        @CompoundIndex(
                name = "activation_rows_owner_valid_stt_gov_uploaded_idx",
                def = "{'uploadedBy': 1, 'validRow': 1, 'nameSttNormalized': 1, 'gouvernoratNormalized': 1, 'uploadedAt': -1}"
        ),
        @CompoundIndex(
                name = "activation_rows_owner_valid_batch_uploaded_idx",
                def = "{'uploadedBy': 1, 'validRow': 1, 'batchId': 1, 'uploadedAt': -1}"
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationAnalyticsRowEntity {

    @Id
    private String id;

    @Indexed
    private String batchId;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private String sourceFileName;
    private Integer rowNumber;
    private Boolean validRow;
    private List<String> validationIssues;
    private CaseType caseType;

    private String crmCase;

    @Indexed
    private String crmCaseNormalized;

    private String dateCreationCrm;
    private LocalDate dateCreationCrmDate;
    private String openingDateSurTimos;
    private LocalDate openingDateSurTimosDate;
    private String statut;
    private String client;
    private String contactClient;
    private String commentaire;
    private String msisdn;
    private String latitudeSite;
    private Double latitudeSiteValue;
    private String longitudeSite;
    private Double longitudeSiteValue;
    private String localite;
    private String delegation;
    private String gouvernorat;

    @Indexed
    private String gouvernoratNormalized;

    private String offre;
    private String desPack;
    private String nameStt;

    @Indexed
    private String nameSttNormalized;

    private String dateAffectationStt;
    private LocalDate dateAffectationSttDate;
    private String repRdv;
    private String datePriseRdv;
    private LocalDate datePriseRdvDate;
    private String causeInjoignabilite;
    private String causeAbandon;
    private String detail;
    private String cmtRdv;
    private String repTravauxStt;
    private String dateFinTrv;
    private LocalDate dateFinTrvDate;
    private String cmtTrvStt;
    private String repZm;
    private String cmtZm;
    private String metrageCable;
    private Double metrageCableValue;
    private String nombreDePv;
    private Double nombreDePvValue;
    private String debitDl;
    private Double debitDlValue;
    private String debitUl;
    private Double debitUlValue;
    private String rsrp;
    private Double rsrpValue;
    private String rsrq;
    private Double rsrqValue;
    private String sinr;
    private Double sinrValue;
    private String siteSecteur;
    private String typeOffreSpeed;
    private String typeClient;
    private String source;
    private String description;

    private Integer assignmentBusinessDays;
    private Integer rdvBusinessDays;
    private Integer executionBusinessDays;
    private Integer endToEndBusinessDays;

    private Boolean completed;
    private Boolean rdvSuccess;
    private Boolean injoignable;
    private Boolean abandon;
    private Boolean blocked;
    private Boolean delayed;

    private Map<String, String> sourceValues;
}
