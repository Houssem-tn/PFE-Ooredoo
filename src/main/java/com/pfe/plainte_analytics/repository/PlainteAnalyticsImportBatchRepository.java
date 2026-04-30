package com.pfe.plainte_analytics.repository;

import com.pfe.plainte_analytics.entity.PlainteAnalyticsImportBatchEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlainteAnalyticsImportBatchRepository extends MongoRepository<PlainteAnalyticsImportBatchEntity, String> {

    Optional<PlainteAnalyticsImportBatchEntity> findTopByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<PlainteAnalyticsImportBatchEntity> findTop6ByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<PlainteAnalyticsImportBatchEntity> findAllByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<PlainteAnalyticsImportBatchEntity> findByUploadedByAndBatchIdInOrderByUploadedAtDesc(String uploadedBy, Collection<String> batchIds);

    long countByUploadedBy(String uploadedBy);
}
