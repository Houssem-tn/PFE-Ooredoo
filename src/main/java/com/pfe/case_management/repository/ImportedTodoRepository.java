package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ImportedTodoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ImportedTodoRepository extends MongoRepository<ImportedTodoEntity, String> {

    List<ImportedTodoEntity> findByZoneManagerNormalizedAndCrmCaseNormalizedIn(
            String zoneManagerNormalized,
            Collection<String> crmCaseNormalized
    );
}
