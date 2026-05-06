package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.SttDistribution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SttDistributionRepository extends MongoRepository<SttDistribution, String> {

    List<SttDistribution> findBySttIdOrderByDistributedAtDesc(String sttId);

    List<SttDistribution> findByTechIdOrderByDistributedAtDesc(String techId);

    List<SttDistribution> findByDpmId(String dpmId);

    List<SttDistribution> findByDpmIdOrderByDistributedAtAsc(String dpmId);
}
