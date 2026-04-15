package com.pfe.case_management.entity;

import com.pfe.case_management.dto.ZmPerformanceStudyResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "saved_zm_performance_studies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedZmPerformanceStudyEntity {

    @Id
    private String id;

    @Indexed
    private Instant savedAt;

    @Indexed
    private String savedBy;

    private List<String> sourceFileNames;
    private List<String> sourceBatchIds;
    private ZmPerformanceStudyResponse study;
}
