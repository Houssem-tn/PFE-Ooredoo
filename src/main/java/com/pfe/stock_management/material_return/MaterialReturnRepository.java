package com.pfe.stock_management.material_return;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialReturnRepository extends MongoRepository<MaterialReturnRequest, String> {

    List<MaterialReturnRequest> findBySttIdOrderByCreatedAtDesc(String sttId);

    List<MaterialReturnRequest> findBySttEmailOrderByCreatedAtDesc(String sttEmail);

    /** Finds all returns for a given STT regardless of whether sttId was stored as
     *  MongoDB user-id or as email (defensive for documents created outside the API). */
    @Query(value = "{ '$or': [ { 'sttId': ?0 }, { 'sttEmail': ?1 } ] }",
           sort  = "{ 'createdAt': -1 }")
    List<MaterialReturnRequest> findBySttIdOrSttEmail(String sttId, String sttEmail);

    List<MaterialReturnRequest> findByZmIdAndStatusOrderByCreatedAtDesc(String zmId, MaterialReturnStatus status);

    List<MaterialReturnRequest> findByZmIdAndStatusInOrderByCreatedAtDesc(String zmId, Collection<MaterialReturnStatus> statuses);

    List<MaterialReturnRequest> findByZmIdOrderByCreatedAtDesc(String zmId);

    List<MaterialReturnRequest> findByZmEmailOrderByCreatedAtDesc(String zmEmail);

    List<MaterialReturnRequest> findBySttIdAndStatusOrderByCreatedAtDesc(String sttId, MaterialReturnStatus status);

    List<MaterialReturnRequest> findByRelatedCaseIdOrderByCreatedAtDesc(String relatedCaseId);

    List<MaterialReturnRequest> findByRelatedCaseIdIn(Collection<String> relatedCaseIds);

    List<MaterialReturnRequest> findByStatusOrderByCreatedAtDesc(MaterialReturnStatus status);

    @Query(value = "{ 'items.instanceId': ?0, 'status': { '$in': ?1 } }")
    List<MaterialReturnRequest> findByTrackedInstanceIdAndStatusIn(
            String instanceId, Collection<MaterialReturnStatus> statuses);

    Optional<MaterialReturnRequest> findByReturnCode(String returnCode);

    long countByStatus(MaterialReturnStatus status);
}
