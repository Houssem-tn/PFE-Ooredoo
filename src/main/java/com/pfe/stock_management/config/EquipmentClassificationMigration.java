package com.pfe.stock_management.config;

import com.pfe.stock_management.entity.Equipment;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Backfills the business-usage classification flags introduced on {@link Equipment}
 * (packItem / usableInAct / usableInPlt / recoverableFromClient).
 *
 * <p>Equipment documents created before this feature have these fields absent. Mongo
 * deserialises a missing primitive {@code boolean} to {@code false}, which would silently
 * make every legacy item non-consumable in ACT and PLT and break existing field execution.
 * To preserve the previous behaviour we set permissive defaults on legacy rows only
 * (usable in ACT and PLT, not a pack item, not recoverable). Admins can then refine pack
 * items and recoverable equipment from the catalogue UI.
 *
 * <p>The migration targets only documents that are still missing the marker field, so it is
 * idempotent and a no-op on subsequent startups.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class EquipmentClassificationMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipmentClassificationMigration.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // "usableInAct" is the canonical marker: if it is absent the document predates the feature.
            Query legacy = new Query(Criteria.where("usableInAct").exists(false));
            Update defaults = new Update()
                    .set("packItem", false)
                    .set("usableInAct", true)
                    .set("usableInPlt", true)
                    .set("recoverableFromClient", false);

            long updated = mongoTemplate.updateMulti(legacy, defaults, Equipment.class).getModifiedCount();
            if (updated > 0) {
                log.info("equipment_classification_backfill applied defaults to {} legacy equipment document(s)", updated);
            } else {
                log.info("equipment_classification_backfill no legacy equipment documents required migration");
            }
        } catch (Exception ex) {
            // Migration must never block application startup.
            log.warn("Failed to backfill equipment classification flags: {}", ex.getMessage());
        }
    }
}
