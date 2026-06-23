package com.pfe.coverage.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "coverage_analysis_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageAnalysisResult {

    @Id
    private String id;

    @Indexed
    private String caseId;

    private String crmCase;
    private String caseType;       // ACTIVATION or PLAINTE
    private String governorate;

    private double latitude;
    private double longitude;

    private boolean inside4G;
    private boolean inside5G;
    private boolean insideWs4G;
    private boolean insideWs5G;

    @Builder.Default
    private List<String> matchedLayerIds = new ArrayList<>();

    private String decision;

    private String analyzedByUserId;
    private String analyzedByEmail;

    @CreatedDate
    private Instant analyzedAt;
}
