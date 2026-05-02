package com.pfe.stock_management.entity;

import com.pfe.stock_management.model.SttStockItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_stt_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttStock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sttId;

    @Builder.Default
    private List<SttStockItem> items = new ArrayList<>();

    private Instant lastUpdatedAt;
}
