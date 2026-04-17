package com.pfe.activation_analytics.entity;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDashboardResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "saved_admin_activation_analytics_studies")
@CompoundIndexes({
        @CompoundIndex(
                name = "saved_activation_studies_owner_saved_idx",
                def = "{'savedBy': 1, 'savedAt': -1}"
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedActivationAnalyticsStudyEntity {

    @Id
    private String id;

    private Instant savedAt;
    private String savedBy;
    private List<String> sourceFileNames;
    private List<String> sourceBatchIds;
    private AdminActivationAnalyticsDashboardResponse dashboardSnapshot;
}
