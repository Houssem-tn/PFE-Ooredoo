package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.enums.DpmStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DpmRepository extends MongoRepository<DpmRequest, String> {

    List<DpmRequest> findByZmIdOrderByCreatedAtDesc(String zmId);

    List<DpmRequest> findBySttIdOrderByCreatedAtDesc(String sttId);

    List<DpmRequest> findBySttIdAndStatusOrderByCreatedAtDesc(String sttId, DpmStatus status);

    Optional<DpmRequest> findByDpmNumber(String dpmNumber);

    long countByDpmNumberStartingWith(String prefix);
}
