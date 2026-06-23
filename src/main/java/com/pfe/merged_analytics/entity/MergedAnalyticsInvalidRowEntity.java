package com.pfe.merged_analytics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "merged_case_invalid_rows")
@CompoundIndex(name = "merged_invalid_study_idx", def = "{'studyId': 1}")
public class MergedAnalyticsInvalidRowEntity {

    @Id
    private String id;
    private String studyId;
    private int rowNumber;
    private String crmCase;
    private String source;
    private String zoneManager;
    private List<String> reasons;
    private String rawPreview;
}
