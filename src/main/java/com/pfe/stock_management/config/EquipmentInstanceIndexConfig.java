package com.pfe.stock_management.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
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
 * <p><b>IMEI / Serial uniqueness is enforced with a {@code partial} unique index</b>, not a
 * sparse one. A {@code sparse} <em>compound</em> index still indexes a document as long as it
 * has a value for <em>at least one</em> keyed field — and {@code equipmentId} is always present —
 * so two units of the same equipment with a {@code null} IMEI would both be indexed as
 * {@code {equipmentId, null}} and collide on the unique constraint (the E11000 seen on DPM
 * confirmation of serial-only / optional-IMEI equipment). A {@code partialFilterExpression} of
 * {@code {field: {$type: "string"}}} indexes only units that actually carry the identifier, so
 * uniqueness applies solely to real, non-empty IMEI / Serial values while any number of units may
 * legitimately have none.
 *
 * <p>The legacy sparse-unique indexes are detected and safely dropped/recreated in place — no
 * collection or document is ever removed.
 */
@Component
@RequiredArgsConstructor
public class EquipmentInstanceIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipmentInstanceIndexConfig.class);

    private static final String COLLECTION = "stock_equipment_instances";
    private static final String IMEI_INDEX = "uniq_equipmentId_normalizedImei";
    private static final String SERIAL_INDEX = "uniq_equipmentId_normalizedSerialNumber";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION);

            // Per-equipment uniqueness for IMEI and Serial Number — partial so units that
            // legitimately have no identifier (null) never collide on a shared null.
            ensurePerEquipmentUniqueIndex(collection, IMEI_INDEX, "normalizedImei");
            ensurePerEquipmentUniqueIndex(collection, SERIAL_INDEX, "normalizedSerialNumber");

            // Audit / lifecycle lookups (plain, non-unique — safe to ensureIndex idempotently).
            var instances = mongoTemplate.indexOps(COLLECTION);
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

            log.info("equipment_instance_indexes_ensured collection={}", COLLECTION);
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure equipment instance indexes: {}", ex.getMessage());
        }
    }

    /**
     * Ensures a per-equipment {@code {equipmentId, field}} unique index that only covers documents
     * whose {@code field} is an actual (non-null) string. Migrates a legacy sparse-unique index of
     * the same name in place: if an index exists but has no {@code partialFilterExpression} it is
     * dropped and recreated; an already-partial index is left untouched (idempotent across restarts).
     * Never drops the collection or any document.
     */
    private void ensurePerEquipmentUniqueIndex(MongoCollection<Document> collection, String indexName, String field) {
        Document keys = new Document("equipmentId", 1).append(field, 1);
        Document partialFilter = new Document(field, new Document("$type", "string"));

        Document existing = findIndexByName(collection, indexName);
        boolean alreadyPartial = existing != null && existing.get("partialFilterExpression") instanceof Document;
        if (alreadyPartial) {
            return; // desired partial-unique index already in place
        }
        if (existing != null) {
            // Legacy sparse-/plain-unique index → replace with the partial one.
            collection.dropIndex(indexName);
            log.info("dropped_stale_unique_index name={} previousDefinition={}", indexName, existing.toJson());
        }
        collection.createIndex(keys, new IndexOptions()
                .unique(true)
                .name(indexName)
                .partialFilterExpression(partialFilter));
        log.info("ensured_partial_unique_index name={} field={} partialFilter={}",
                indexName, field, partialFilter.toJson());
    }

    private Document findIndexByName(MongoCollection<Document> collection, String name) {
        for (Document index : collection.listIndexes()) {
            if (name.equals(index.getString("name"))) {
                return index;
            }
        }
        return null;
    }
}
