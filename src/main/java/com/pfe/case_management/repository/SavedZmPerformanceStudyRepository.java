package com.pfe.case_management.repository;

import com.pfe.case_management.entity.SavedZmPerformanceStudyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SavedZmPerformanceStudyRepository extends MongoRepository<SavedZmPerformanceStudyEntity, String> {

    List<SavedZmPerformanceStudyEntity> findAllByOrderBySavedAtDesc();
}
