package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.StockMovementType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    private String id;

    private StockMovementType movementType;

    private String fromActorId;
    private String fromActorRole;
    private String toActorId;
    private String toActorRole;

    private String technicianId;
    private String caseId;
    private String equipmentId;
    private String equipmentName;
    private int quantity;

    private String referenceId;
    private String comment;

    private Instant createdAt;
    private String createdBy;
    private Instant performedAt;
    private String performedBy;
}
