package com.pfe.merged_analytics.repository;

import com.pfe.merged_analytics.entity.MergedAnalyticsInvalidRowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MergedAnalyticsInvalidRowRepository extends MongoRepository<MergedAnalyticsInvalidRowEntity, String> {

    Page<MergedAnalyticsInvalidRowEntity> findAllByStudyId(String studyId, Pageable pageable);

    void deleteAllByStudyId(String studyId);
}
