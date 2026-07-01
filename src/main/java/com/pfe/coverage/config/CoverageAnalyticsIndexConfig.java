package com.pfe.coverage.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the index that backs the viewport (bounding-box) coverage-map query
 * ({@code CoverageAnalyticsPointService.getPoints}). The Network Coverage map loads
 * activation (SOURCE = MES) markers for the visible map bounds only, so every request
 * filters {@code studyId} + {@code source} (equality) then the {@code latitude} /
 * {@code longitude} value fields (range).
 *
 * <p>Created programmatically because the project does not enable Spring Data's
 * annotation-based auto-index-creation, so the {@code @CompoundIndex} annotations on
 * {@code MergedAnalyticsRowEntity} would be no-ops. A GeoJSON 2dsphere index is not used
 * because coordinates are stored as two separate numeric fields (not a geometry object);
 * a compound B-tree index over those fields is what actually accelerates the range query.
 * {@code ensureIndex} is idempotent.</p>
 */
@Component
@RequiredArgsConstructor
public class CoverageAnalyticsIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoverageAnalyticsIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            mongoTemplate.indexOps("merged_case_analytics_rows")
                    .ensureIndex(new Index()
                            .on("studyId", Sort.Direction.ASC)
                            .on("source", Sort.Direction.ASC)
                            .on("latitudeSiteValue", Sort.Direction.ASC)
                            .on("longitudeSiteValue", Sort.Direction.ASC)
                            .named("coverage_bbox_idx"));
            log.info("coverage_bbox_index_ensured collection=merged_case_analytics_rows index=coverage_bbox_idx");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure coverage bbox index: {}", ex.getMessage());
        }
    }
}
