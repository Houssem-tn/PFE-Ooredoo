package com.pfe.activation_analytics.repository;

import com.pfe.activation_analytics.entity.SavedActivationAnalyticsStudyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedActivationAnalyticsStudyRepository extends MongoRepository<SavedActivationAnalyticsStudyEntity, String> {

    List<SavedActivationAnalyticsStudyEntity> findAllBySavedByOrderBySavedAtDesc(String savedBy);

    Optional<SavedActivationAnalyticsStudyEntity> findByIdAndSavedBy(String id, String savedBy);
}
