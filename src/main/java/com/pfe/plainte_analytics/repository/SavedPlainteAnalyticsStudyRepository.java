package com.pfe.plainte_analytics.repository;

import com.pfe.plainte_analytics.entity.SavedPlainteAnalyticsStudyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlainteAnalyticsStudyRepository extends MongoRepository<SavedPlainteAnalyticsStudyEntity, String> {

    List<SavedPlainteAnalyticsStudyEntity> findAllBySavedByOrderBySavedAtDesc(String savedBy);

    Optional<SavedPlainteAnalyticsStudyEntity> findByIdAndSavedBy(String id, String savedBy);
}
