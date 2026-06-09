package com.pfe.case_management.entity;

import com.pfe.case_management.model.ImportedTodoSourceType;
import com.pfe.case_management.model.TodoTimelineEvent;
import com.pfe.case_management.model.ZmTodoBusinessType;
import com.pfe.case_management.model.ZmTodoWorkflowStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "imported_zm_todos")
@CompoundIndexes({
        @CompoundIndex(
                name = "imported_zm_todos_owner_source_crm_idx",
                def = "{'sourceType': 1, 'uploadedBy': 1, 'crmCaseNormalized': 1}"
        ),
        @CompoundIndex(
                name = "imported_zm_todos_owner_uploaded_idx",
                def = "{'uploadedBy': 1, 'uploadedAt': -1}"
        ),
        @CompoundIndex(
                name = "imported_zm_todos_field_work_dup_idx",
                def = "{'uploadedBy': 1, 'businessType': 1, 'crmCaseNormalized': 1}"
        )
})
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

    // --- Field work workflow fields ---

    @Indexed
    private ZmTodoBusinessType businessType;

    @Indexed
    private ZmTodoWorkflowStatus workflowStatus;

    private Double decimalLatitude;
    private Double decimalLongitude;

    private String assignedSttId;
    private String assignedSttName;
    private String assignedTechId;
    private String assignedTechName;

    private String priority;
    private Integer agingDays;

    private Boolean workCreated;
    private LocalDateTime workCreatedAt;

    private List<TodoTimelineEvent> timeline;

    private LocalDateTime lastActionAt;
    private LocalDateTime closedAt;
}
