package com.pfe.merged_analytics.repository;

import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import com.pfe.merged_analytics.model.MergedProcessingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MergedAnalyticsStudyRepository extends MongoRepository<MergedAnalyticsStudyEntity, String> {

    Optional<MergedAnalyticsStudyEntity> findByStudyId(String studyId);

    Optional<MergedAnalyticsStudyEntity> findByStudyIdAndUploadedBy(String studyId, String uploadedBy);

    List<MergedAnalyticsStudyEntity> findAllByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    /** Most recently uploaded study in the given processing state (used by Network Coverage). */
    Optional<MergedAnalyticsStudyEntity> findFirstByProcessingStatusOrderByUploadedAtDesc(MergedProcessingStatus status);
}
