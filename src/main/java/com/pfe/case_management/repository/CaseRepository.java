package com.pfe.case_management.repository;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaseRepository extends MongoRepository<CaseEntity, String> {

    List<CaseEntity> findByType(CaseType type);

    List<CaseEntity> findByTypeAndZmEmailIgnoreCase(CaseType type, String zmEmail);

    List<CaseEntity> findByTypeAndSttEmailIgnoreCase(CaseType type, String sttEmail);

    List<CaseEntity> findByTypeAndTechEmailIgnoreCase(CaseType type, String techEmail);

    List<CaseEntity> findByTypeAndStatusIn(CaseType type, List<CaseStatus> statuses);

    Optional<CaseEntity> findByCrmCase(String crmCase);
    Optional<CaseEntity> findByTypeAndCrmCase(CaseType type, String crmCase);
    List<CaseEntity> findByTypeAndCrmCaseIn(CaseType type, Collection<String> crmCases);

    // Module-scoped duplicate lookup: PLT and RES imports both persist as type=PLAINTE but use
    // distinct import sources, so the same CRM case may legitimately exist once per module.
    List<CaseEntity> findByTypeAndSourceAndCrmCaseIn(CaseType type, String source, Collection<String> crmCases);

    long countByTypeAndZmIdAndStatusIn(CaseType type, String zmId, List<CaseStatus> statuses);
}
