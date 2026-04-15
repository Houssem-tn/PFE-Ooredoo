package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ZmTodoAnalysisEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ZmTodoAnalysisRepository extends MongoRepository<ZmTodoAnalysisEntity, String> {

    List<ZmTodoAnalysisEntity> findAllByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    Optional<ZmTodoAnalysisEntity> findByIdAndUploadedBy(String id, String uploadedBy);
}
