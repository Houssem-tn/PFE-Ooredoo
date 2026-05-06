package com.pfe.stock_management.entity;

import com.pfe.stock_management.model.UsageItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_tech_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechStockUsage {

    @Id
    private String id;

    @Indexed
    private String techId;

    @Indexed
    private String caseId;

    private String caseReference;

    @Builder.Default
    private List<UsageItem> items = new ArrayList<>();

    private Instant declaredAt;
    private String notes;
}
