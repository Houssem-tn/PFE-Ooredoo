package com.pfe.case_management.repository;

import com.pfe.case_management.entity.SavedZmPerformanceStudyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedZmPerformanceStudyRepository extends MongoRepository<SavedZmPerformanceStudyEntity, String> {

    List<SavedZmPerformanceStudyEntity> findAllBySavedByOrderBySavedAtDesc(String savedBy);

    Optional<SavedZmPerformanceStudyEntity> findByIdAndSavedBy(String id, String savedBy);
}
