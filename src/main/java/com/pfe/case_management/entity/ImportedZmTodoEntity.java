package com.pfe.case_management.entity;

import com.pfe.case_management.model.ImportedTodoSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Document(collection = "imported_zm_todos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportedZmTodoEntity {

    @Id
    private String id;

    @Indexed
    private String importBatchId;

    @Indexed
    private ImportedTodoSourceType sourceType;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private String sourceFileName;
    private String managerFilterUsed;

    private String crmCase;

    @Indexed
    private String crmCaseNormalized;

    private Integer rowNumber;
    private Boolean validRow;
    private List<String> validationIssues;

    private String fxDateCrm;
    private String contact1;
    private String contact2;
    private String msisdn;
    private String gouvernorat;

    @Indexed
    private String gouvernoratNormalized;

    private String description;
    private String latitude;
    private String longitude;
    private String openDateSurTimos;
    private LocalDate openDateSurTimosDate;
    private String dateAffectationStt;
    private LocalDate dateAffectationSttDate;
    private Integer slaBusinessDays;
    private String stt;

    @Indexed
    private String sttNormalized;

    private String statut;
    private String zoneManager;

    @Indexed
    private String zoneManagerNormalized;

    private Map<String, String> sourceValues;
}
