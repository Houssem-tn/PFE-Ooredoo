package com.pfe.coverage.repository;

import com.pfe.coverage.entity.CoverageAnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CoverageAnalysisResultRepository extends MongoRepository<CoverageAnalysisResult, String> {

    Optional<CoverageAnalysisResult> findByCaseId(String caseId);

    List<CoverageAnalysisResult> findByGovernorate(String governorate);

    // All cases where no coverage layer matched
    List<CoverageAnalysisResult> findByInside4GFalseAndInside5GFalseAndInsideWs4GFalseAndInsideWs5GFalse();
}
