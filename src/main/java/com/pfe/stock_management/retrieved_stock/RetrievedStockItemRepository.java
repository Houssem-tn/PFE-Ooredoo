package com.pfe.stock_management.retrieved_stock;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RetrievedStockItemRepository extends MongoRepository<RetrievedStockItem, String> {

    List<RetrievedStockItem> findByTechnicianIdAndStatusOrderByRetrievedAtDesc(
            String technicianId, RetrievedStockItemStatus status);

    List<RetrievedStockItem> findByZmIdAndStatusOrderByRetrievedAtDesc(
            String zmId, RetrievedStockItemStatus status);

    List<RetrievedStockItem> findByCollectedByZmIdAndStatusOrderByCollectedAtDesc(
            String collectedByZmId, RetrievedStockItemStatus status);

    List<RetrievedStockItem> findByCaseIdOrderByRetrievedAtDesc(String caseId);

    List<RetrievedStockItem> findAllByOrderByRetrievedAtDesc();
}
