package com.pfe.plainte_analytics.repository;

import com.pfe.plainte_analytics.entity.PlainteAnalyticsRowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface PlainteAnalyticsRowRepository extends MongoRepository<PlainteAnalyticsRowEntity, String> {

    List<PlainteAnalyticsRowEntity> findByUploadedByAndCrmCaseNormalizedIn(String uploadedBy, Collection<String> crmCaseNormalized);

    List<PlainteAnalyticsRowEntity> findByUploadedByAndValidRowTrue(String uploadedBy);

    List<PlainteAnalyticsRowEntity> findByUploadedByAndBatchIdInAndValidRowTrue(String uploadedBy, Collection<String> batchIds);

    long countByUploadedBy(String uploadedBy);

    long countByUploadedByAndValidRowTrue(String uploadedBy);
}
