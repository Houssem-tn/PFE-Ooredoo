package com.pfe.activation_analytics.repository;

import com.pfe.activation_analytics.entity.ActivationAnalyticsRowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ActivationAnalyticsRowRepository extends MongoRepository<ActivationAnalyticsRowEntity, String> {

    List<ActivationAnalyticsRowEntity> findByUploadedByAndCrmCaseNormalizedIn(String uploadedBy, Collection<String> crmCaseNormalized);

    List<ActivationAnalyticsRowEntity> findByUploadedByAndValidRowTrue(String uploadedBy);

    List<ActivationAnalyticsRowEntity> findByUploadedByAndBatchIdInAndValidRowTrue(String uploadedBy, Collection<String> batchIds);

    long countByUploadedBy(String uploadedBy);

    long countByUploadedByAndValidRowTrue(String uploadedBy);
}
