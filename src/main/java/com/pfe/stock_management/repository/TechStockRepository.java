package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.TechStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TechStockRepository extends MongoRepository<TechStock, String> {

    Optional<TechStock> findByTechId(String techId);

    List<TechStock> findBySttId(String sttId);
}
