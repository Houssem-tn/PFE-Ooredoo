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

@Document(collection = "admin_activation_import_batches")
@CompoundIndexes({
        @CompoundIndex(
                name = "activation_batches_owner_uploaded_idx",
                def = "{'uploadedBy': 1, 'uploadedAt': -1}"
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationAnalyticsImportBatchEntity {

    @Id
    private String id;

    @Indexed
    private String batchId;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private CaseType caseType;
    private String fileName;
    private Integer totalRows;
    private Integer insertedRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer duplicateRows;
    private Integer distinctStts;
    private Integer distinctGovernorates;
}
