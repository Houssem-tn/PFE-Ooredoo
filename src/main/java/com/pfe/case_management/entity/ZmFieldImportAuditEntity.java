package com.pfe.case_management.entity;

import com.pfe.case_management.model.ZmTodoBusinessType;
import com.pfe.case_management.model.ZmTodoDispatchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "zm_field_import_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZmFieldImportAuditEntity {

    @Id
    private String id;

    @Indexed
    private String importBatchId;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private String sourceFileName;

    @Indexed
    private ZmTodoBusinessType businessType;

    private String target;

    @Indexed
    private ZmTodoDispatchMode dispatchMode;

    private String dispatchModeUserName;
    private Integer totalRows;
    private Integer matchedConnectedZmRows;
    private Integer enCoursRows;
    private Integer successRows;
    private Integer createdWorkTodoCount;
    private Integer createdCaseCount;
    private Integer duplicateCount;
    private Integer ignoredOtherZmCount;
    private Integer ignoredStatusCount;
    private Integer invalidRowsCount;
    private Integer failedRows;
    private String message;
    private String analysisId;
}
