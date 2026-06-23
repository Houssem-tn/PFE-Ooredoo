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
 * Ensures the indexes backing DPM source traceability: per-DPM source balances and
 * the FIFO allocation lookups. Created programmatically because the project does not
 * enable Spring Data's annotation-based auto-index creation, so entity-level
 * {@code @Indexed} annotations would be no-ops. {@code ensureIndex} is idempotent.
 */
@Component
@RequiredArgsConstructor
public class StockAllocationIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockAllocationIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var balances = mongoTemplate.indexOps("stock_dpm_source_balances");
            // FIFO allocation lookup: STT pool + equipment, oldest confirmed DPM first.
            balances.ensureIndex(new Index()
                    .on("sttId", Sort.Direction.ASC)
                    .on("equipmentId", Sort.Direction.ASC)
                    .on("confirmedAt", Sort.Direction.ASC)
                    .named("sttId_equipmentId_confirmedAt"));
            balances.ensureIndex(new Index()
                    .on("dpmId", Sort.Direction.ASC)
                    .named("dpmId"));

            var allocations = mongoTemplate.indexOps("stock_dpm_allocations");
            allocations.ensureIndex(new Index()
                    .on("dpmId", Sort.Direction.ASC)
                    .on("allocatedAt", Sort.Direction.ASC)
                    .named("dpmId_allocatedAt"));
            allocations.ensureIndex(new Index()
                    .on("distributionId", Sort.Direction.ASC)
                    .named("distributionId"));
            allocations.ensureIndex(new Index()
                    .on("technicianId", Sort.Direction.ASC)
                    .named("technicianId"));

            log.info("stock_allocation_indexes_ensured collections=[stock_dpm_source_balances,stock_dpm_allocations]");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure stock allocation indexes: {}", ex.getMessage());
        }
    }
}
