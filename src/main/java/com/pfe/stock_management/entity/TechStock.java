package com.pfe.stock_management.entity;

import com.pfe.stock_management.model.TechStockItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_tech_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechStock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String techId;

    private String sttId;

    @Builder.Default
    private List<TechStockItem> items = new ArrayList<>();

    private Instant lastUpdatedAt;
}
