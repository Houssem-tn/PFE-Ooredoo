package com.pfe.stock_management.config;

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
 * Ensures the indexes backing the technician Retrieved-Stock / ZM collection workflow. Created
 * programmatically because the project does not enable Spring Data's annotation-based auto-index
 * creation, so a bare {@code @Indexed} on the entity would be a no-op. {@code ensureIndex} is
 * idempotent across restarts.
 */
@Component
@RequiredArgsConstructor
public class RetrievedStockIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrievedStockIndexConfig.class);

    private static final String COLLECTION = "stock_retrieved_items";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var items = mongoTemplate.indexOps(COLLECTION);

            // Technician's own pending/collected bucket ("my retrieved stock").
            items.ensureIndex(new Index()
                    .on("technicianId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("technicianId_status"));

            // ZM's pending-collection queue and collected bucket.
            items.ensureIndex(new Index()
                    .on("zmId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("zmId_status"));

            // ZM's own collected-stock view (post-collection).
            items.ensureIndex(new Index()
                    .on("collectedByZmId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("collectedByZmId_status"));

            // Case-linked lookup (case detail / audit).
            items.ensureIndex(new Index()
                    .on("caseId", Sort.Direction.ASC)
                    .named("caseId"));

            log.info("retrieved_stock_indexes_ensured collection={}", COLLECTION);
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure retrieved stock indexes: {}", ex.getMessage());
        }
    }
}
