package com.pfe.merged_analytics.repository;

import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MergedAnalyticsRowRepository extends MongoRepository<MergedAnalyticsRowEntity, String> {

    long countByStudyId(String studyId);

    void deleteAllByStudyId(String studyId);
}
