package com.pfe.stock_management.material_return;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialReturnRepository extends MongoRepository<MaterialReturnRequest, String> {

    List<MaterialReturnRequest> findBySttIdOrderByCreatedAtDesc(String sttId);

    List<MaterialReturnRequest> findByZmIdAndStatusOrderByCreatedAtDesc(String zmId, MaterialReturnStatus status);

    List<MaterialReturnRequest> findByZmIdOrderByCreatedAtDesc(String zmId);

    List<MaterialReturnRequest> findByRelatedCaseIdOrderByCreatedAtDesc(String relatedCaseId);

    List<MaterialReturnRequest> findByStatusOrderByCreatedAtDesc(MaterialReturnStatus status);

    Optional<MaterialReturnRequest> findByReturnCode(String returnCode);

    long countByStatus(MaterialReturnStatus status);
}
