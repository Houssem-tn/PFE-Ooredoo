package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.CaseStockConsumption;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CaseStockConsumptionRepository extends MongoRepository<CaseStockConsumption, String> {

    Optional<CaseStockConsumption> findByCaseId(String caseId);

    List<CaseStockConsumption> findByTechnicianIdOrderByConsumedAtDesc(String technicianId);

    List<CaseStockConsumption> findBySttIdOrderByConsumedAtDesc(String sttId);

    List<CaseStockConsumption> findByZmIdOrderByConsumedAtDesc(String zmId);

    List<CaseStockConsumption> findBySttIdInOrderByConsumedAtDesc(List<String> sttIds);

    List<CaseStockConsumption> findByTechnicianIdInOrderByConsumedAtDesc(List<String> techIds);

    List<CaseStockConsumption> findByConsumedAtBetweenOrderByConsumedAtDesc(Instant from, Instant to);

    List<CaseStockConsumption> findAllByOrderByConsumedAtDesc();
}
