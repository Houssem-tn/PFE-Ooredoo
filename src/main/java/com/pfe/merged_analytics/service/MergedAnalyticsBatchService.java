package com.pfe.merged_analytics.service;

import com.pfe.merged_analytics.entity.MergedAnalyticsInvalidRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MergedAnalyticsBatchService {

    private static final int INSERT_CHUNK_SIZE = 5_000;

    private final MongoTemplate mongoTemplate;

    public void insertValidRowsInBatches(List<MergedAnalyticsRowEntity> entities) {
        if (entities == null || entities.isEmpty()) return;

        for (int start = 0; start < entities.size(); start += INSERT_CHUNK_SIZE) {
            int end = Math.min(start + INSERT_CHUNK_SIZE, entities.size());
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MergedAnalyticsRowEntity.class)
                    .insert(entities.subList(start, end))
                    .execute();
        }
    }

    public void insertInvalidRowsInBatches(List<MergedAnalyticsInvalidRowEntity> entities) {
        if (entities == null || entities.isEmpty()) return;

        for (int start = 0; start < entities.size(); start += INSERT_CHUNK_SIZE) {
            int end = Math.min(start + INSERT_CHUNK_SIZE, entities.size());
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MergedAnalyticsInvalidRowEntity.class)
                    .insert(entities.subList(start, end))
                    .execute();
        }
    }
}
