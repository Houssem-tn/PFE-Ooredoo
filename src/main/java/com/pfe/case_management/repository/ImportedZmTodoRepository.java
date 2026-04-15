package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ImportedZmTodoEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ImportedZmTodoRepository extends MongoRepository<ImportedZmTodoEntity, String> {

    List<ImportedZmTodoEntity> findByUploadedByAndSourceTypeAndCrmCaseNormalizedIn(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            Collection<String> crmCaseNormalized
    );
}
