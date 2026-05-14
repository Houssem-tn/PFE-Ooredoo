package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.StockMovement;
import com.pfe.stock_management.enums.StockMovementType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockMovementRepository extends MongoRepository<StockMovement, String> {

    List<StockMovement> findByReferenceIdOrderByPerformedAtDesc(String referenceId);

    List<StockMovement> findByFromActorIdOrToActorIdOrderByPerformedAtDesc(String fromActorId, String toActorId);

    List<StockMovement> findByMovementTypeOrderByPerformedAtDesc(StockMovementType movementType);
}
