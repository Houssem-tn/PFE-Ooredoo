package com.pfe.stock_management.entity;

import com.pfe.stock_management.model.DistributionItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_stt_distributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttDistribution {

    @Id
    private String id;

    private String sttId;
    private String techId;
    private String techName;
    private String techEmail;
    private String dpmId;

    @Builder.Default
    private List<DistributionItem> items = new ArrayList<>();

    private Instant distributedAt;
    private String notes;
    private String distributedBy;
}
