package com.pfe.case_management.messaging;

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
 * Indexes backing the case discussion queries: thread reads by caseId and
 * unread-badge lookups by receiver. Created programmatically because the project
 * does not enable annotation-based auto-index-creation.
 */
@Component
@RequiredArgsConstructor
public class CaseMessageIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CaseMessageIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var ops = mongoTemplate.indexOps("case_messages");
            ops.ensureIndex(new Index()
                    .on("caseId", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.ASC)
                    .named("caseId_createdAt"));
            ops.ensureIndex(new Index()
                    .on("receiverEmail", Sort.Direction.ASC)
                    .on("read", Sort.Direction.ASC)
                    .named("receiverEmail_read"));
            log.info("case_message_indexes_ensured collection=case_messages");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure case message indexes: {}", ex.getMessage());
        }
    }
}
