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
 * Ensures the indexes backing per-unit equipment tracking. Created programmatically because
 * the project does not enable Spring Data's annotation-based auto-index creation.
 *
 * <p>The IMEI / serial uniqueness is scoped <b>per equipment type</b> (compound with
 * {@code equipmentId}) and {@code sparse} so units that legitimately have no IMEI (serial-only
 * mode) or no serial (imei-only mode) do not collide on a shared null. {@code ensureIndex}
 * is idempotent.
 */
@Component
@RequiredArgsConstructor
public class EquipmentInstanceIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipmentInstanceIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var instances = mongoTemplate.indexOps("stock_equipment_instances");

            // Per-equipment uniqueness for IMEI and Serial Number (sparse → blanks are stored as null).
            instances.ensureIndex(new Index()
                    .on("equipmentId", Sort.Direction.ASC)
                    .on("normalizedImei", Sort.Direction.ASC)
                    .unique()
                    .sparse()
                    .named("uniq_equipmentId_normalizedImei"));
            instances.ensureIndex(new Index()
                    .on("equipmentId", Sort.Direction.ASC)
                    .on("normalizedSerialNumber", Sort.Direction.ASC)
                    .unique()
                    .sparse()
                    .named("uniq_equipmentId_normalizedSerialNumber"));

            // Audit / lifecycle lookups.
            instances.ensureIndex(new Index()
                    .on("currentOwnerId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("currentOwnerId_status"));
            instances.ensureIndex(new Index()
                    .on("caseId", Sort.Direction.ASC)
                    .named("caseId"));
            instances.ensureIndex(new Index()
                    .on("sourceDpmId", Sort.Direction.ASC)
                    .named("sourceDpmId"));
            instances.ensureIndex(new Index()
                    .on("returnId", Sort.Direction.ASC)
                    .named("returnId"));

            log.info("equipment_instance_indexes_ensured collection=stock_equipment_instances");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure equipment instance indexes: {}", ex.getMessage());
        }
    }
}
