package com.pfe.merged_analytics.entity;

import com.pfe.merged_analytics.model.MergedProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "merged_case_analytics_studies")
@CompoundIndexes({
    @CompoundIndex(name = "merged_studies_owner_uploaded_idx", def = "{'uploadedBy': 1, 'uploadedAt': -1}")
})
public class MergedAnalyticsStudyEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String studyId;

    private String fileName;
    private String fileType;
    private String uploadedBy;
    private Instant uploadedAt;

    private int totalRows;
    private int processedRows;
    private int validRows;
    private int invalidRows;
    private int warningRows;
    private int duplicateRows;

    private MergedProcessingStatus processingStatus;
    private Long processingDurationMs;

    private List<String> sourcesFound;
    private List<String> zoneManagersFound;
    private List<String> sttsFound;
    private List<String> governoratesFound;
    private List<String> statusesFound;

    private Instant dateMin;
    private Instant dateMax;

    private List<String> missingOptionalColumns;
    private String errorMessage;
}
