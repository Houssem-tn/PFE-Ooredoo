package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ImportedTodoEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ImportedTodoRepository extends MongoRepository<ImportedTodoEntity, String> {

    List<ImportedTodoEntity> findByUploadedByAndSourceTypeInAndZoneManagerNormalizedInAndCrmCaseNormalizedIn(
            String uploadedBy,
            Collection<ImportedTodoSourceType> sourceTypes,
            Collection<String> zoneManagerNormalized,
            Collection<String> crmCaseNormalized
    );
}
