package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.SttStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SttStockRepository extends MongoRepository<SttStock, String> {

    Optional<SttStock> findBySttId(String sttId);
}
