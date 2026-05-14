package com.pfe.case_management.repository;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PlainteRepository extends MongoRepository<CaseEntity, String> {

    List<CaseEntity> findByType(CaseType type);

    Optional<CaseEntity> findByTypeAndCrmCase(CaseType type, String crmCase);
}
