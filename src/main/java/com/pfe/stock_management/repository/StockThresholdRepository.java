package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.StockThreshold;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StockThresholdRepository extends MongoRepository<StockThreshold, String> {

    List<StockThreshold> findByZmId(String zmId);

    List<StockThreshold> findByZmIdAndEnabledTrue(String zmId);

    Optional<StockThreshold> findByZmIdAndItemId(String zmId, String itemId);

    boolean existsByZmIdAndItemId(String zmId, String itemId);
}
