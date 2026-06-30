package com.pfe.stock_management.config;

import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.enums.TrackingMode;
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
 * Backfills the {@link TrackingMode} field introduced on {@link Equipment}.
 *
 * <p>Equipment documents created before per-unit tracking have this field absent. Mongo
 * deserialises a missing enum to {@code null}; to preserve the previous quantity-based
 * behaviour we set legacy rows to {@link TrackingMode#NONE} so existing DPM / STT / TECH /
 * usage / return flows keep working unchanged. Admins can then promote individual devices
 * (ODU, IDU, modem) to a tracked mode from the catalogue UI.
 *
 * <p>The migration targets only documents still missing {@code trackingMode}, so it is
 * idempotent and a no-op on subsequent startups.
 */
@Component
@Order(21)
@RequiredArgsConstructor
public class EquipmentTrackingModeMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipmentTrackingModeMigration.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Query legacy = new Query(Criteria.where("trackingMode").exists(false));
            Update defaults = new Update().set("trackingMode", TrackingMode.NONE.name());

            long updated = mongoTemplate.updateMulti(legacy, defaults, Equipment.class).getModifiedCount();
            if (updated > 0) {
                log.info("equipment_tracking_mode_backfill set NONE on {} legacy equipment document(s)", updated);
            } else {
                log.info("equipment_tracking_mode_backfill no legacy equipment documents required migration");
            }
        } catch (Exception ex) {
            // Migration must never block application startup.
            log.warn("Failed to backfill equipment tracking mode: {}", ex.getMessage());
        }
    }
}
