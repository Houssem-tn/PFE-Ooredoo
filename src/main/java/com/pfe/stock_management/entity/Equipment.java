package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.EquipmentCategory;
import com.pfe.stock_management.enums.StockUnit;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stock_equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    private String id;

    private String name;
    private String description;
    private StockUnit unit;
    private EquipmentCategory category;

    private String imageFileId;

    @Transient
    private String imageUrl;

    @Builder.Default
    private boolean active = true;

    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
