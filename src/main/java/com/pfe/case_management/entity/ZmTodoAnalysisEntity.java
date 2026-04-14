package com.pfe.case_management.entity;

import com.pfe.case_management.dto.ZmTodoAnalyticsResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "zm_todo_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZmTodoAnalysisEntity {

    @Id
    private String id;

    @Indexed
    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    @Indexed
    private String importBatchId;

    private String sourceFileName;
    private String connectedZmName;
    private String managerNameUsed;
    private ZmTodoAnalyticsResponse analytics;
}
