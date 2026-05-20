package com.pfe.merged_analytics.repository;

import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MergedAnalyticsStudyRepository extends MongoRepository<MergedAnalyticsStudyEntity, String> {

    Optional<MergedAnalyticsStudyEntity> findByStudyId(String studyId);

    Optional<MergedAnalyticsStudyEntity> findByStudyIdAndUploadedBy(String studyId, String uploadedBy);

    List<MergedAnalyticsStudyEntity> findAllByUploadedByOrderByUploadedAtDesc(String uploadedBy);
}
