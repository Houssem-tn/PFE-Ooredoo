package com.pfe.integration;

import com.pfe.stock_management.config.EquipmentInstanceIndexConfig;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.enums.TrackingMode;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MongoDB integration test for the per-equipment IMEI/Serial uniqueness indexes
 * (see {@link EquipmentInstanceIndexConfig}). Runs against the `test` profile / `pfe_test`
 * database and requires a reachable local MongoDB — same tradeoff as
 * {@link CaseRepositoryIntegrationTest} (no Testcontainers here).
 *
 * <p>Proves the fix for the DPM-confirmation E11000 on {@code {equipmentId, normalizedImei: null}}:
 * a partial unique index lets any number of units with a null IMEI coexist for one equipment while
 * still rejecting a genuine duplicate real IMEI. Self-cleaning: only the documents it inserts (a
 * unique per-run equipmentId) are removed, and the shared index is left in its correct partial form.
 */
@SpringBootTest
class EquipmentInstanceIndexIntegrationTest {

    private static final String COLLECTION = "stock_equipment_instances";
    private static final String IMEI_INDEX = "uniq_equipmentId_normalizedImei";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private EquipmentInstanceIndexConfig indexConfig;

    private final String equipmentId = "itest-eq-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        mongoTemplate.remove(
                new Query(Criteria.where("equipmentId").is(equipmentId)),
                EquipmentInstance.class);
    }

    @Test
    void partialIndex_allowsMultipleNullImeiUnits_butRejectsRealDuplicate() {
        // The context startup ran the ApplicationRunner, so the partial index is already in place.
        Document imeiIndex = indexByName(IMEI_INDEX);
        assertThat(imeiIndex).as("IMEI uniqueness index must exist").isNotNull();
        assertThat(imeiIndex.get("partialFilterExpression"))
                .as("must be a PARTIAL index, not a plain/sparse one").isNotNull();
        assertThat(imeiIndex.getBoolean("unique", false)).isTrue();

        // Two serial-only units of the SAME equipment, both with a null IMEI → must coexist.
        assertThatCode(() -> {
            mongoTemplate.insert(unit("SN-A", null));
            mongoTemplate.insert(unit("SN-B", null));
        }).as("multiple null-IMEI units for one equipment must be allowed").doesNotThrowAnyException();

        // A genuine duplicate real IMEI for the same equipment must still be rejected.
        mongoTemplate.insert(unit("SN-C", "REALIMEI-1"));
        assertThatThrownBy(() -> mongoTemplate.insert(unit("SN-D", "REALIMEI-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void legacySparseUniqueIndex_isMigratedToPartialInPlace() {
        var col = mongoTemplate.getCollection(COLLECTION);

        // Recreate the pre-fix definition: a sparse+unique compound index (no partial filter).
        col.dropIndex(IMEI_INDEX);
        col.createIndex(
                Indexes.ascending("equipmentId", "normalizedImei"),
                new IndexOptions().unique(true).sparse(true).name(IMEI_INDEX));
        assertThat(indexByName(IMEI_INDEX).get("partialFilterExpression"))
                .as("precondition: legacy index has no partial filter").isNull();

        // Re-run the migration runner.
        indexConfig.run(null);

        Document migrated = indexByName(IMEI_INDEX);
        assertThat(migrated.get("partialFilterExpression"))
                .as("index must now be partial").isNotNull();
        assertThat(migrated.getBoolean("sparse", false))
                .as("partial index must not also be sparse").isFalse();
        assertThat(migrated.getBoolean("unique", false)).isTrue();
    }

    private EquipmentInstance unit(String serial, String imei) {
        return EquipmentInstance.builder()
                .equipmentId(equipmentId)
                .equipmentName("ITest IDU")
                .trackingMode(TrackingMode.SERIAL_OR_IMEI)
                .serialNumber(serial)
                .normalizedSerialNumber(serial)
                .imei(imei)
                .normalizedImei(imei)
                .build();
    }

    private Document indexByName(String name) {
        for (Document index : mongoTemplate.getCollection(COLLECTION).listIndexes()) {
            if (name.equals(index.getString("name"))) {
                return index;
            }
        }
        return null;
    }
}
