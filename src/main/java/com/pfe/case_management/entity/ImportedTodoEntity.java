package com.pfe.case_management.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "imported_todos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportedTodoEntity {

    @Id
    private String id;

    @Indexed
    private String importBatchId;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private String sourceFileName;
    private String managerFilterUsed;

    private String crmCase;

    @Indexed
    private String crmCaseNormalized;

    private String fxDateCrm;
    private String contact1;
    private String contact2;
    private String msisdn;
    private String gouvernorat;
    private String description;
    private String latitude;
    private String longitude;
    private String openDateSurTimos;
    private String dateAffectationStt;
    private String stt;
    private String statut;
    private String zoneManager;

    @Indexed
    private String zoneManagerNormalized;

    private Map<String, String> sourceValues;
}
