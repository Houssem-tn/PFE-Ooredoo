package com.pfe.activation_analytics.repository;

import com.pfe.activation_analytics.entity.ActivationAnalyticsImportBatchEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivationAnalyticsImportBatchRepository extends MongoRepository<ActivationAnalyticsImportBatchEntity, String> {

    Optional<ActivationAnalyticsImportBatchEntity> findTopByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<ActivationAnalyticsImportBatchEntity> findTop6ByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<ActivationAnalyticsImportBatchEntity> findAllByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    List<ActivationAnalyticsImportBatchEntity> findByUploadedByAndBatchIdInOrderByUploadedAtDesc(String uploadedBy, Collection<String> batchIds);

    long countByUploadedBy(String uploadedBy);
}
