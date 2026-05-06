package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.TechStockUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TechStockUsageRepository extends MongoRepository<TechStockUsage, String> {

    List<TechStockUsage> findAllByOrderByDeclaredAtDesc();

    List<TechStockUsage> findByTechIdOrderByDeclaredAtDesc(String techId);

    List<TechStockUsage> findByTechIdInOrderByDeclaredAtDesc(List<String> techIds);
}
