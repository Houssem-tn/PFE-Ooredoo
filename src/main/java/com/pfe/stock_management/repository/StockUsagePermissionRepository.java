package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.StockUsagePermissionRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StockUsagePermissionRepository extends MongoRepository<StockUsagePermissionRequest, String> {

    List<StockUsagePermissionRequest> findBySttIdAndStatus(String sttId, String status);

    List<StockUsagePermissionRequest> findByCaseId(String caseId);

    List<StockUsagePermissionRequest> findByTechId(String techId);

    /** Find active (non-terminal) permission for a given case+tech. */
    Optional<StockUsagePermissionRequest> findByCaseIdAndTechIdAndStatusIn(
            String caseId, String techId, List<String> statuses);
}
