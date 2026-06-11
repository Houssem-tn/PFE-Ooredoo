package com.pfe.case_management.config;

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
 * Ensures the indexes that back the role-scoped, module-scoped, paginated case board queries
 * ({@code CaseService.getCasesPage} / {@code getBoardSummary}). Created programmatically because
 * the project does not enable Spring Data's annotation-based auto-index-creation, so entity
 * {@code @CompoundIndex} annotations would be no-ops. {@code ensureIndex} is idempotent.
 */
@Component
@RequiredArgsConstructor
public class CaseIndexConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CaseIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var ops = mongoTemplate.indexOps("cases");
            ops.ensureIndex(new Index()
                    .on("type", Sort.Direction.ASC)
                    .on("zmEmail", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("type_zmEmail_createdAt"));
            ops.ensureIndex(new Index()
                    .on("type", Sort.Direction.ASC)
                    .on("sttEmail", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("type_sttEmail_createdAt"));
            ops.ensureIndex(new Index()
                    .on("type", Sort.Direction.ASC)
                    .on("techEmail", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("type_techEmail_createdAt"));
            ops.ensureIndex(new Index()
                    .on("type", Sort.Direction.ASC)
                    .on("source", Sort.Direction.ASC)
                    .named("type_source"));
            ops.ensureIndex(new Index()
                    .on("type", Sort.Direction.ASC)
                    .on("caseModule", Sort.Direction.ASC)
                    .named("type_caseModule"));
            log.info("case_board_indexes_ensured collection=cases");
        } catch (Exception ex) {
            // Index creation must never block application startup.
            log.warn("Failed to ensure case board indexes: {}", ex.getMessage());
        }
    }
}
