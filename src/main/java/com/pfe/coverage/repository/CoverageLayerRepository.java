package com.pfe.coverage.repository;

import com.pfe.coverage.entity.CoverageLayer;
import com.pfe.coverage.enums.CoverageLayerStatus;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CoverageLayerRepository extends MongoRepository<CoverageLayer, String> {

    List<CoverageLayer> findByStatus(CoverageLayerStatus status);

    List<CoverageLayer> findByStatusAndTechnologyAndLayerType(
            CoverageLayerStatus status,
            CoverageTechnology technology,
            CoverageLayerType layerType
    );

    List<CoverageLayer> findByStatusAndGovernorate(CoverageLayerStatus status, String governorate);

    List<CoverageLayer> findByUploadedByUserId(String uploadedByUserId);
}
