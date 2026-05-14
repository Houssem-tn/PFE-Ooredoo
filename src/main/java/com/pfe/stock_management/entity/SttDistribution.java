package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.DistributionStatus;
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
    private String sttName;
    private String sttEmail;
    private String techId;
    private String techName;
    private String techEmail;
    private String dpmId;

    @Builder.Default
    private List<DistributionItem> items = new ArrayList<>();

    @Builder.Default
    private DistributionStatus status = DistributionStatus.PENDING_TECH_CONFIRMATION;

    private Instant distributedAt;
    private Instant confirmedAt;
    private String confirmedBy;
    private String notes;
    private String distributedBy;
}
